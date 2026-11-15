package com.autodjmorp

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Two independent Media3 decks for a genuine overlapping audio transition.
 *
 * The caller supplies playable MediaItems. YouTube Music catalog lookup and
 * stream resolution are deliberately kept outside this class.
 */
class CrossfadeEngine(
    context: Context,
    crossfadeDurationMs: Long = 6_000L,
    private val onTrackChanged: (MediaItem) -> Unit = {},
    private val onPlaybackError: (PlaybackException) -> Unit = {},
) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    private var firstDeck = createDeck()
    private var secondDeck = createDeck()
    private var currentDeck = firstDeck
    private var nextDeck = secondDeck
    private var queue: List<MediaItem> = emptyList()
    private var queueIndex = 0
    private var crossfadeDuration = crossfadeDurationMs.coerceAtLeast(1_000L)
    private var crossfadeStarted = false
    private var fadeStartedAt = 0L
    private var released = false

    val player: ExoPlayer
        get() = currentDeck

    private fun createDeck(): ExoPlayer =
        ExoPlayer.Builder(appContext)
            .setAudioAttributes(audioAttributes, false)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { deck ->
                deck.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        onPlaybackError(error)
                    }
                })
            }

    fun setCrossfadeDuration(durationMs: Long) {
        crossfadeDuration = durationMs.coerceAtLeast(1_000L)
    }

    fun play(items: List<MediaItem>, startIndex: Int = 0) {
        checkMainThread()
        if (released || items.isEmpty()) return
        stop()
        queue = items.toList()
        queueIndex = startIndex.coerceIn(queue.indices)
        currentDeck.setMediaItem(queue[queueIndex])
        currentDeck.volume = 1f
        currentDeck.prepare()
        currentDeck.play()
        onTrackChanged(queue[queueIndex])
        handler.removeCallbacks(positionCheck)
        handler.post(positionCheck)
    }

    fun pause() {
        checkMainThread()
        currentDeck.pause()
        nextDeck.pause()
    }

    fun resume() {
        checkMainThread()
        currentDeck.play()
    }

    fun seekTo(positionMs: Long) {
        checkMainThread()
        currentDeck.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun stop() {
        checkMainThread()
        handler.removeCallbacks(positionCheck)
        handler.removeCallbacks(fadeStep)
        crossfadeStarted = false
        firstDeck.stop()
        firstDeck.clearMediaItems()
        secondDeck.stop()
        secondDeck.clearMediaItems()
        queue = emptyList()
        queueIndex = 0
    }

    private val positionCheck = object : Runnable {
        override fun run() {
            if (released) return
            if (!crossfadeStarted && currentDeck.isPlaying && queueIndex < queue.lastIndex) {
                val duration = currentDeck.duration
                val position = currentDeck.currentPosition
                if (duration > 0L && duration != C.TIME_UNSET) {
                    val fadeLength = minOf(crossfadeDuration, (duration / 2L).coerceAtLeast(1_000L))
                    if (duration - position <= fadeLength) beginCrossfade(fadeLength)
                }
            }
            handler.postDelayed(this, 200L)
        }
    }

    private fun beginCrossfade(durationMs: Long) {
        if (crossfadeStarted || queueIndex >= queue.lastIndex || released) return
        crossfadeStarted = true
        nextDeck.stop()
        nextDeck.clearMediaItems()
        nextDeck.volume = 0f
        nextDeck.setMediaItem(queue[queueIndex + 1])
        nextDeck.prepare()
        nextDeck.playWhenReady = true

        nextDeck.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && crossfadeStarted && !released) {
                    nextDeck.removeListener(this)
                    if (currentDeck.isPlaying) {
                        fadeStartedAt = SystemClock.uptimeMillis()
                        handler.post(fadeStep)
                    } else {
                        crossfadeStarted = false
                        nextDeck.pause()
                        nextDeck.volume = 0f
                    }
                } else if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                    nextDeck.removeListener(this)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                nextDeck.removeListener(this)
                crossfadeStarted = false
                nextDeck.stop()
                nextDeck.clearMediaItems()
                nextDeck.volume = 0f
            }
        })
        nextDeck.play()
        fadeDurationForRun = durationMs
    }

    private var fadeDurationForRun = crossfadeDuration

    private val fadeStep = object : Runnable {
        override fun run() {
            if (released || !crossfadeStarted) return
            if (!currentDeck.isPlaying || !nextDeck.isPlaying) {
                handler.postDelayed(this, 50L)
                return
            }
            val progress = ((SystemClock.uptimeMillis() - fadeStartedAt).toFloat() /
                fadeDurationForRun.toFloat()).coerceIn(0f, 1f)
            // Equal-power curves keep perceived loudness more even in the overlap.
            currentDeck.volume = kotlin.math.cos(progress * Math.PI / 2.0).toFloat()
            nextDeck.volume = kotlin.math.sin(progress * Math.PI / 2.0).toFloat()

            if (progress >= 1f) {
                currentDeck.pause()
                currentDeck.stop()
                currentDeck.clearMediaItems()
                currentDeck.volume = 0f
                val oldCurrent = currentDeck
                currentDeck = nextDeck
                nextDeck = oldCurrent
                queueIndex++
                currentDeck.volume = 1f
                crossfadeStarted = false
                onTrackChanged(queue[queueIndex])
            } else {
                handler.postDelayed(this, 50L)
            }
        }
    }

    fun release() {
        checkMainThread()
        if (released) return
        released = true
        handler.removeCallbacks(positionCheck)
        handler.removeCallbacks(fadeStep)
        firstDeck.release()
        secondDeck.release()
        queue = emptyList()
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "CrossfadeEngine must be controlled from the main thread"
        }
    }
}
