
package com.autodjmorp

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import android.content.res.ColorStateList

class MainActivity : Activity() {

    private var autoAdvanceEnabled = false
    private var lastAutoAdvancedTrack = ""
    private var lastQueueSignature = ""
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var currentSongText: TextView
    private lateinit var artistText: TextView
    private lateinit var connectionText: TextView
    private lateinit var queueLayout: LinearLayout
    private lateinit var playStatus: TextView
    private lateinit var trackProgress: ProgressBar
    private lateinit var playControl: TextView

    private val refreshTask = object : Runnable {
        override fun run() {
            updateCurrentSong()
            updatePlaybackIndicator()
            refreshQueueFromMorphe()
            checkAutoAdvance()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 25, 22, 25)
            setBackgroundColor(Color.rgb(247, 247, 247))
        }

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val title = TextView(this).apply {
            text = "AUTO DJ"
            textSize = 30f
            setTextColor(Color.rgb(59, 53, 60))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        content.addView(title)

        val subtitle = TextView(this).apply {
            text = "Tu mezclador automático"
            textSize = 14f
            setTextColor(Color.rgb(105, 99, 106))
            gravity = Gravity.CENTER
            setPadding(0, 5, 0, 25)
        }

        content.addView(subtitle)

        val playerWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 8)
        }

        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 10)
        }
        statusRow.addView(TextView(this).apply {
            text = "♪"
            textSize = 25f
            setTextColor(Color.rgb(59, 53, 60))
            setPadding(0, 0, 9, 0)
        })
        playStatus = TextView(this).apply {
            text = "pause"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.5f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(20, 5, 20, 5)
            background = makeBackground(Color.rgb(59, 53, 60), 30)
        }
        statusRow.addView(playStatus)
        playerWrapper.addView(statusRow)

        val playerBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 30, 24, 24)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(2, Color.rgb(59, 53, 60))
            }
        }

        currentSongText = TextView(this).apply {
            text = "Esperando canción..."
            textSize = 19f
            setTextColor(Color.rgb(59, 53, 60))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 5)
        }
        playerBox.addView(currentSongText)

        artistText = TextView(this).apply {
            text = "Abre YouTube Music Morphe"
            textSize = 13f
            setTextColor(Color.rgb(105, 99, 106))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 22)
        }
        playerBox.addView(artistText)

        trackProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = 0
            progressTintList = ColorStateList.valueOf(Color.rgb(59, 53, 60))
            progressBackgroundTintList = ColorStateList.valueOf(Color.rgb(220, 217, 221))
        }
        playerBox.addView(trackProgress, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 3
        ).apply { bottomMargin = 23 })

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val previousButton = makeIconButton("previous", "Canción anterior")
        playControl = makeIconButton("pause", "Reproducir o pausar")
        val nextButton = makeIconButton("next", "Siguiente canción")
        previousButton.setOnClickListener { sendMediaCommand("previous") }
        playControl.setOnClickListener { sendMediaCommand("playpause") }
        nextButton.setOnClickListener { sendMediaCommand("next") }
        controls.addView(previousButton, LinearLayout.LayoutParams(42, 42).apply {
            setMargins(18, 0, 18, 0)
        })
        controls.addView(playControl, LinearLayout.LayoutParams(42, 42).apply {
            setMargins(18, 0, 18, 0)
        })
        controls.addView(nextButton, LinearLayout.LayoutParams(42, 42).apply {
            setMargins(18, 0, 18, 0)
        })
        playerBox.addView(controls)
        playerWrapper.addView(playerBox)
        connectionText = TextView(this).apply {
            text = "Esperando conexión con Morphe..."
            textSize = 11f
            setTextColor(Color.rgb(105, 99, 106))
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 0)
        }
        playerWrapper.addView(connectionText)
        content.addView(playerWrapper, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 12 })

        content.addView(makeTitle("CONTROLES DE MÚSICA"))

        autoAdvanceEnabled = getSharedPreferences("AutoDJMusic", MODE_PRIVATE)
            .getBoolean("auto_advance", false)

        val autoSwitch = Switch(this).apply {
            text = "AVANCE AUTOMÁTICO"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(0, 25, 0, 8)
            isChecked = autoAdvanceEnabled
        }

        autoSwitch.setOnCheckedChangeListener { _, enabled ->
            autoAdvanceEnabled = enabled
            getSharedPreferences("AutoDJMusic", MODE_PRIVATE).edit()
                .putBoolean("auto_advance", enabled).apply()
            lastAutoAdvancedTrack = ""
            Toast.makeText(this,
                if (enabled) "Avance automático activado" else "Avance automático desactivado",
                Toast.LENGTH_SHORT).show()
        }

        content.addView(autoSwitch)

        val autoDescription = TextView(this).apply {
            text = "Pasa a la siguiente canción cuando la actual esté por terminar. Morphe controla el audio; esta versión no superpone dos canciones."
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, 15)
        }
        content.addView(autoDescription)

        val queueTitle = makeTitle("PRÓXIMAS CANCIONES")
        content.addView(queueTitle)

        queueLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
            background = makeBackground(
                Color.rgb(31, 36, 48),
                16
            )
        }

        content.addView(queueLayout)

        val refreshButton = makeButton("↻ ACTUALIZAR COLA DE MORPHE")
        refreshButton.setOnClickListener {
            lastQueueSignature = ""
            refreshQueueFromMorphe()
        }

        content.addView(
            refreshButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                55
            ).apply {
                topMargin = 20
            }
        )

        scroll.addView(content)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)

        refreshQueueFromMorphe()
    }

    private fun makeTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(Color.rgb(59, 53, 60))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 15, 0, 12)
        }
    }

    private fun makeButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = makeBackground(
                Color.rgb(59, 53, 60),
                14
            )
        }
    }

    private fun makeIconButton(kind: String, description: String): TextView {
        return TextView(this).apply {
            contentDescription = description
            gravity = Gravity.CENTER
            textSize = if (kind == "pause") 21f else 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (kind == "pause") Color.WHITE else Color.rgb(59, 53, 60))
            text = when (kind) {
                "previous" -> "◀|"
                "next" -> "|▶"
                else -> "Ⅱ"
            }
            if (kind == "pause") {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.rgb(59, 53, 60))
                }
            } else {
                background = null
            }
            isClickable = true
            isFocusable = true
        }
    }

    private fun controlButtonParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            64,
            64
        ).apply {
            setMargins(10, 8, 10, 8)
        }
    }

    private fun buttonParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            0,
            58,
            1f
        ).apply {
            setMargins(4, 5, 4, 5)
        }
    }

    private fun makeBackground(
        color: Int,
        radius: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
        }
    }

    private fun updateCurrentSong() {
        val preferences = getSharedPreferences(
            "AutoDJMusic",
            MODE_PRIVATE
        )

        val title = preferences.getString(
            "music_title",
            ""
        ) ?: ""

        val artist = preferences.getString(
            "music_artist",
            ""
        ) ?: ""

        if (title.isNotBlank()) {
            currentSongText.text = title
            artistText.text = artist
            connectionText.text = "● Conectado a YouTube Music Morphe"
            connectionText.setTextColor(
                Color.rgb(0, 220, 150)
            )
        } else {
            currentSongText.text = "Esperando canción..."
            artistText.text = "Reproduce una canción en Morphe"
            connectionText.text = "Esperando conexión..."
            connectionText.setTextColor(Color.GRAY)
        }
    }

    private fun updatePlaybackIndicator() {
        try {
            val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            val component = ComponentName(this, MusicNotificationListener::class.java)
            val controllers = manager.getActiveSessions(component)
            val savedPackage = getSharedPreferences("AutoDJMusic", MODE_PRIVATE)
                .getString("music_package", "").orEmpty()
            val controller = controllers.firstOrNull {
                savedPackage.isNotBlank() && it.packageName.equals(savedPackage, true)
            } ?: controllers.firstOrNull {
                it.packageName.contains("youtube", true) || it.packageName.contains("morphe", true)
            }
            val state = controller?.playbackState
            val playing = state?.state == PlaybackState.STATE_PLAYING
            playStatus.text = if (playing) "play" else "pause"
            playControl.text = if (playing) "Ⅱ" else "▶"
            val duration = controller?.metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            val position = state?.position ?: 0L
            trackProgress.progress = if (duration > 0L) {
                ((position.coerceAtLeast(0L).toDouble() / duration.toDouble()) * 1000).toInt().coerceIn(0, 1000)
            } else 0
        } catch (_: Exception) {
            playStatus.text = "pause"
            playControl.text = "▶"
            trackProgress.progress = 0
        }
    }

    private fun sendMediaCommand(action: String) {
        try {
            val manager = getSystemService(
                MEDIA_SESSION_SERVICE
            ) as MediaSessionManager

            val component = ComponentName(
                this,
                MusicNotificationListener::class.java
            )

            val controllers = manager.getActiveSessions(component)

            val preferences = getSharedPreferences(
                "AutoDJMusic",
                MODE_PRIVATE
            )

            val savedPackage = preferences.getString(
                "music_package",
                ""
            ) ?: ""

            val controller = controllers.firstOrNull {
                it.packageName.equals(
                    savedPackage,
                    ignoreCase = true
                )
            } ?: controllers.firstOrNull {
                it.packageName.contains(
                    "youtube",
                    ignoreCase = true
                ) || it.packageName.contains(
                    "morphe",
                    ignoreCase = true
                )
            }

            if (controller == null) {
                Toast.makeText(
                    this,
                    "No se encontró el reproductor. Abre Morphe y reproduce una canción.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            when (action) {
                "previous" -> {
                    controller.transportControls.skipToPrevious()
                }

                "next" -> {
                    controller.transportControls.skipToNext()
                }

                "playpause" -> {
                    val state = controller.playbackState?.state

                    if (state == PlaybackState.STATE_PLAYING) {
                        controller.transportControls.pause()
                    } else {
                        controller.transportControls.play()
                    }
                }
            }

        } catch (e: SecurityException) {
            Toast.makeText(
                this,
                "Activa el permiso de notificaciones de Auto DJ.",
                Toast.LENGTH_LONG
            ).show()

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "No se pudo controlar el reproductor.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun refreshQueueFromMorphe() {
        if (!::queueLayout.isInitialized) return
        val queue = try {
            val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            val component = ComponentName(this, MusicNotificationListener::class.java)
            val controllers = manager.getActiveSessions(component)
            val savedPackage = getSharedPreferences("AutoDJMusic", MODE_PRIVATE)
                .getString("music_package", "").orEmpty()
            val controller = controllers.firstOrNull {
                savedPackage.isNotBlank() && it.packageName.equals(savedPackage, true)
            } ?: controllers.firstOrNull {
                it.packageName.contains("youtube", true) || it.packageName.contains("morphe", true)
            }
            controller?.queue.orEmpty().mapNotNull {
                it.description.title?.toString()?.trim()
            }.filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }

        val signature = queue.joinToString("|")
        if (signature == lastQueueSignature && queueLayout.childCount > 0) return
        lastQueueSignature = signature
        queueLayout.removeAllViews()

        if (queue.isEmpty()) {
            queueLayout.addView(TextView(this).apply {
                text = "Morphe no está compartiendo su lista de reproducción ahora. Los controles de canción seguirán funcionando."
                setTextColor(Color.LTGRAY)
                textSize = 14f
            })
            return
        }

        queue.take(30).forEachIndexed { index, song ->
            queueLayout.addView(TextView(this).apply {
                text = "${index + 1}. $song"
                textSize = 15f
                setTextColor(Color.WHITE)
                setPadding(5, 9, 5, 9)
            })
        }
    }

    private fun checkAutoAdvance() {
        if (!autoAdvanceEnabled) return
        try {
            val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            val component = ComponentName(this, MusicNotificationListener::class.java)
            val savedPackage = getSharedPreferences("AutoDJMusic", MODE_PRIVATE)
                .getString("music_package", "").orEmpty()
            val controller = manager.getActiveSessions(component).firstOrNull {
                savedPackage.isNotBlank() && it.packageName.equals(savedPackage, true)
            } ?: manager.getActiveSessions(component).firstOrNull {
                it.packageName.contains("youtube", true) || it.packageName.contains("morphe", true)
            } ?: return

            val state = controller.playbackState ?: return
            if (state.state != PlaybackState.STATE_PLAYING) return
            val metadata = controller.metadata ?: return
            val duration = metadata.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION)
            val position = state.position
            if (duration <= 0L || position < 0L) return

            val key = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE).orEmpty() +
                "|" + duration
            if (duration - position in 1..6500 && key != lastAutoAdvancedTrack) {
                lastAutoAdvancedTrack = key
                controller.transportControls.skipToNext()
            }
        } catch (_: Exception) {
        }
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(refreshTask)
        handler.post(refreshTask)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshTask)
    }
}
