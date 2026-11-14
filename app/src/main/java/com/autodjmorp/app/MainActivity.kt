
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

class MainActivity : Activity() {

    private val queue = mutableListOf<String>()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var currentSongText: TextView
    private lateinit var artistText: TextView
    private lateinit var connectionText: TextView
    private lateinit var queueLayout: LinearLayout

    private val refreshTask = object : Runnable {
        override fun run() {
            updateCurrentSong()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 25, 22, 25)
            setBackgroundColor(Color.rgb(15, 17, 24))
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

        val currentCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
            background = makeBackground(
                Color.rgb(31, 36, 48),
                18
            )
        }

        val currentLabel = TextView(this).apply {
            text = "♫ REPRODUCIENDO AHORA"
            textSize = 13f
            setTextColor(Color.rgb(0, 220, 150))
            typeface = Typeface.DEFAULT_BOLD
        }

        currentCard.addView(currentLabel)

        currentSongText = TextView(this).apply {
            text = "Esperando canción..."
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 15, 0, 5)
        }

        currentCard.addView(currentSongText)

        artistText = TextView(this).apply {
            text = "Abre YouTube Music Morphe"
            textSize = 14f
            setTextColor(Color.LTGRAY)
        }

        currentCard.addView(artistText)

        connectionText = TextView(this).apply {
            text = "Esperando conexión..."
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, 12, 0, 0)
        }

        currentCard.addView(connectionText)

        content.addView(
            currentCard,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 20
            }
        )

        val controlsTitle = makeTitle("CONTROLES DE MÚSICA")
        content.addView(controlsTitle)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val previousButton = makeControlButton("⏮", false, "Canción anterior")
        val playButton = makeControlButton("▶", true, "Reproducir o pausar")
        val nextButton = makeControlButton("⏭", false, "Siguiente canción")

        previousButton.setOnClickListener {
            sendMediaCommand("previous")
        }

        playButton.setOnClickListener {
            sendMediaCommand("playpause")
        }

        nextButton.setOnClickListener {
            sendMediaCommand("next")
        }

        controls.addView(previousButton, controlButtonParams())
        controls.addView(playButton, controlButtonParams())
        controls.addView(nextButton, controlButtonParams())

        content.addView(controls)

        val mixSwitch = Switch(this).apply {
            text = "MEZCLA AUTOMÁTICA"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(0, 25, 0, 20)
        }

        mixSwitch.setOnCheckedChangeListener { _, enabled ->
            val message = if (enabled) {
                "Mezcla automática activada"
            } else {
                "Mezcla automática desactivada"
            }

            Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT
            ).show()
        }

        content.addView(mixSwitch)

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

        val addButton = makeButton("+ AGREGAR CANCIÓN")
        addButton.setOnClickListener {
            addSong()
        }

        content.addView(
            addButton,
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

        refreshQueue()
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
                Color.rgb(0, 145, 105),
                14
            )
        }
    }

    private fun makeControlButton(
        symbol: String,
        primary: Boolean,
        description: String
    ): TextView {
        return TextView(this).apply {
            text = symbol
            contentDescription = description
            gravity = Gravity.CENTER
            textSize = if (primary) 27f else 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(
                    if (primary) Color.rgb(116, 82, 220)
                    else Color.rgb(35, 40, 55)
                )
                setStroke(
                    if (primary) 0 else 2,
                    if (primary) Color.TRANSPARENT else Color.rgb(91, 103, 133)
                )
            }
            elevation = if (primary) 8f else 3f
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

    private fun addSong() {
        val input = EditText(this).apply {
            hint = "Nombre de la canción"
            setSingleLine(true)
        }

        AlertDialog.Builder(this)
            .setTitle("Agregar canción")
            .setView(input)
            .setPositiveButton("Agregar") { _, _ ->
                val song = input.text.toString().trim()

                if (song.isNotEmpty()) {
                    queue.add(song)
                    refreshQueue()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun refreshQueue() {
        if (!::queueLayout.isInitialized) return

        queueLayout.removeAllViews()

        if (queue.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "Todavía no hay canciones en la lista"
                setTextColor(Color.LTGRAY)
                textSize = 14f
            }

            queueLayout.addView(emptyText)
            return
        }

        queue.forEachIndexed { index, song ->
            val songText = TextView(this).apply {
                text = "${index + 1}. $song"
                textSize = 15f
                setTextColor(Color.WHITE)
                setPadding(5, 10, 5, 10)
            }

            queueLayout.addView(songText)
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
