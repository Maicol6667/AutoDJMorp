
package com.autodjmorp

import android.app.Activity
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
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
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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
    private lateinit var playControl: IconControlView
    private lateinit var captureStatusText: TextView

    companion object {
        private const val REQUEST_AUDIO_PERMISSION = 701
        private const val REQUEST_CAPTURE_PERMISSION = 702
    }

    private val refreshTask = object : Runnable {
        override fun run() {
            updateCurrentSong()
            updatePlaybackIndicator()
            updateCaptureResult()
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
            setBackgroundColor(Color.BLACK)
        }

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val title = TextView(this).apply {
            text = "AUTO DJ"
            textSize = 30f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        content.addView(title)

        val subtitle = TextView(this).apply {
            text = "Tu mezclador automático"
            textSize = 14f
            setTextColor(Color.LTGRAY)
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
            setTextColor(Color.WHITE)
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
                setColor(Color.BLACK)
                setStroke(2, Color.rgb(75, 70, 80))
            }
        }

        currentSongText = TextView(this).apply {
            text = "Esperando canción..."
            textSize = 19f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 5)
        }
        playerBox.addView(currentSongText)

        artistText = TextView(this).apply {
            text = "Abre YouTube Music Morphe"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 22)
        }
        playerBox.addView(artistText)

        trackProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = 0
            progressTintList = ColorStateList.valueOf(Color.rgb(160, 130, 255))
            progressBackgroundTintList = ColorStateList.valueOf(Color.rgb(75, 75, 82))
        }
        playerBox.addView(trackProgress, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 6
        ).apply { bottomMargin = 26 })

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val previousButton = IconControlView(this, "previous")
        playControl = IconControlView(this, "play")
        val nextButton = IconControlView(this, "next")
        previousButton.contentDescription = "Canción anterior"
        playControl.contentDescription = "Reproducir o pausar"
        nextButton.contentDescription = "Siguiente canción"
        previousButton.setOnClickListener { sendMediaCommand("previous") }
        playControl.setOnClickListener { sendMediaCommand("playpause") }
        nextButton.setOnClickListener { sendMediaCommand("next") }
        controls.addView(previousButton, LinearLayout.LayoutParams(58, 58).apply {
            setMargins(20, 0, 20, 0)
        })
        controls.addView(playControl, LinearLayout.LayoutParams(68, 68).apply {
            setMargins(20, 0, 20, 0)
        })
        controls.addView(nextButton, LinearLayout.LayoutParams(58, 58).apply {
            setMargins(20, 0, 20, 0)
        })
        playerBox.addView(controls)
        playerWrapper.addView(playerBox)
        connectionText = TextView(this).apply {
            text = "Esperando conexión con Morphe..."
            textSize = 11f
            setTextColor(Color.LTGRAY)
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
            text = "MEZCLA AUTOMÁTICA"
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
            text = "Al quedar 3 segundos, AutoDJMorp pasa a la siguiente canción y Morphe realiza el fundido cruzado si está activado."
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, 15)
        }
        content.addView(autoDescription)


        content.addView(makeTitle("PRUEBA DE AUDIO"))

        val captureButton = makeButton("PROBAR CAPTURA DE AUDIO DE MORPHE")
        captureButton.setOnClickListener { startCaptureTest() }
        content.addView(captureButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 55
        ).apply { topMargin = 6 })

        captureStatusText = TextView(this).apply {
            text = "Prueba pendiente. Reproduce una canción en Morphe y pulsa el botón."
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(4, 10, 4, 15)
        }
        content.addView(captureStatusText)

        val queueHeading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 15, 0, 12)
        }
        queueHeading.addView(TextView(this).apply {
            text = "♫"
            textSize = 23f
            setTextColor(Color.rgb(180, 155, 255))
            setPadding(0, 0, 10, 0)
        })
        queueHeading.addView(TextView(this).apply {
            text = "PRÓXIMAS CANCIONES"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        })
        content.addView(queueHeading)

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


    private fun startCaptureTest() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            captureStatusText.text = "Esta prueba requiere Android 10 o superior."
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO_PERMISSION)
            return
        }
        requestCaptureConsent()
    }

    private fun requestCaptureConsent() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE_PERMISSION)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestCaptureConsent()
            } else {
                captureStatusText.text = "No se concedió el permiso necesario para realizar la prueba."
            }
        }
    }

    @Deprecated("Deprecated in Android API; retained for compatibility with this app")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CAPTURE_PERMISSION) return
        if (resultCode != RESULT_OK || data == null) {
            captureStatusText.text = "Prueba cancelada. No se capturó ningún audio."
            return
        }
        getSharedPreferences("AutoDJMusic", MODE_PRIVATE).edit()
            .putString("capture_test_result", "RUNNING")
            .putString("capture_test_detail", "")
            .apply()
        val serviceIntent = Intent(this, AudioCaptureTestService::class.java).apply {
            putExtra("projection_result_code", resultCode)
            putExtra("projection_data", data)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            captureStatusText.text = "Comprobando el audio de Morphe durante unos segundos..."
        } catch (e: Exception) {
            captureStatusText.text = "No se pudo iniciar la prueba de audio."
        }
    }

    private fun updateCaptureResult() {
        if (!::captureStatusText.isInitialized) return
        val prefs = getSharedPreferences("AutoDJMusic", MODE_PRIVATE)
        when (prefs.getString("capture_test_result", "NONE")) {
            "RUNNING" -> captureStatusText.text = "Comprobando si Morphe permite capturar audio..."
            "CAPTURED" -> {
                captureStatusText.text = "✓ Se recibió audio de Morphe. Esto confirma captura, pero todavía no significa que podamos separar dos canciones para mezclarlas."
                captureStatusText.setTextColor(Color.rgb(0, 220, 150))
            }
            "NO_AUDIO" -> {
                captureStatusText.text = prefs.getString(
                    "capture_test_detail",
                    "No se recibió audio. Morphe podría bloquear la captura."
                )
                captureStatusText.setTextColor(Color.rgb(255, 180, 90))
            }
            else -> captureStatusText.text =
                "Prueba pendiente. Reproduce una canción en Morphe y pulsa el botón."
        }
    }

    private fun makeTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(Color.WHITE)
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
            playControl.setPlaying(playing)
            val duration = controller?.metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            val position = state?.position ?: 0L
            trackProgress.progress = if (duration > 0L) {
                ((position.coerceAtLeast(0L).toDouble() / duration.toDouble()) * 1000).toInt().coerceIn(0, 1000)
            } else 0
        } catch (_: Exception) {
            playStatus.text = "pause"
            playControl.setPlaying(false)
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
        val queueData = try {
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
            val currentQueueId = controller?.let { active ->
                active.queue?.firstOrNull { item ->
                    item.description.title?.toString() == active.metadata?.getString(
                        android.media.MediaMetadata.METADATA_KEY_TITLE
                    )
                }?.queueId
            }
            Pair(currentQueueId, controller?.queue.orEmpty().mapNotNull { item ->
                val title = item.description.title?.toString()?.trim()
                if (title.isNullOrBlank()) null else Pair(item.queueId, title)
            })
        } catch (_: Exception) {
            Pair(null, emptyList())
        }

        val signature = queueData.first.toString() + "|" +
            queueData.second.joinToString("|") { it.first.toString() + ":" + it.second }
        if (signature == lastQueueSignature && queueLayout.childCount > 0) return
        lastQueueSignature = signature
        queueLayout.removeAllViews()

        if (queueData.second.isEmpty()) {
            queueLayout.addView(TextView(this).apply {
                text = "Morphe no está compartiendo su lista de reproducción ahora. Los controles de canción seguirán funcionando."
                setTextColor(Color.LTGRAY)
                textSize = 14f
            })
            return
        }

        queueData.second.take(30).forEachIndexed { index, item ->
            val isCurrent = item.first == queueData.first
            queueLayout.addView(TextView(this).apply {
                text = (if (isCurrent) "▶  " else "   ") + (index + 1).toString() + ". " + item.second +
                    if (isCurrent) "   · REPRODUCIENDO" else ""
                textSize = if (isCurrent) 15f else 14f
                setTextColor(if (isCurrent) Color.rgb(190, 165, 255) else Color.WHITE)
                if (isCurrent) typeface = Typeface.DEFAULT_BOLD
                setPadding(5, 10, 5, 10)
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
            if (duration - position in 1..3000 && key != lastAutoAdvancedTrack) {
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
    private inner class IconControlView(context: android.content.Context, private val kind: String) :
        View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 3.8f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            style = Paint.Style.STROKE
        }
        private var playing = false

        fun setPlaying(value: Boolean) {
            playing = value
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val unit = minOf(width, height) / 2.8f
            paint.style = Paint.Style.FILL
            if (kind == "previous" || kind == "next") {
                val direction = if (kind == "previous") -1f else 1f
                val path = Path().apply {
                    moveTo(cx + direction * unit * 0.65f, cy)
                    lineTo(cx - direction * unit * 0.35f, cy - unit * 0.72f)
                    lineTo(cx - direction * unit * 0.35f, cy + unit * 0.72f)
                    close()
                }
                canvas.drawPath(path, paint)
                paint.style = Paint.Style.STROKE
                val x = cx - direction * unit * 0.72f
                canvas.drawLine(x, cy - unit * 0.72f, x, cy + unit * 0.72f, paint)
                val x2 = cx + direction * unit * 0.92f
                canvas.drawLine(x2, cy - unit * 0.72f, x2, cy + unit * 0.72f, paint)
            } else if (playing) {
                paint.strokeWidth = 5f
                canvas.drawRoundRect(cx - unit * 0.42f, cy - unit * 0.72f,
                    cx - unit * 0.08f, cy + unit * 0.72f, 3f, 3f, paint)
                canvas.drawRoundRect(cx + unit * 0.08f, cy - unit * 0.72f,
                    cx + unit * 0.42f, cy + unit * 0.72f, 3f, 3f, paint)
            } else {
                val path = Path().apply {
                    moveTo(cx - unit * 0.42f, cy - unit * 0.78f)
                    lineTo(cx + unit * 0.72f, cy)
                    lineTo(cx - unit * 0.42f, cy + unit * 0.78f)
                    close()
                }
                canvas.drawPath(path, paint)
            }
        }
    }

}
