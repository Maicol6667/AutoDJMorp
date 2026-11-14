
package com.autodjmorp

import android.app.Activity
import android.app.AlertDialog
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

    private val songs = mutableListOf<String>()
    private lateinit var queueLayout: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var currentSongText: TextView
    private lateinit var artistText: TextView

    private val handler = Handler(Looper.getMainLooper())

    private val updateTask = object : Runnable {
        override fun run() {
            updateCurrentSong()
            handler.postDelayed(this, 1000)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun makeBackground(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun text(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }
    }

    private fun addSpace(layout: LinearLayout, height: Int) {
        val space = View(this)
        layout.addView(space, LinearLayout.LayoutParams(1, dp(height)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(15, 23, 42)
        window.navigationBarColor = Color.rgb(15, 23, 42)

        val backgroundColor = Color.rgb(15, 23, 42)
        val cardColor = Color.rgb(30, 41, 59)
        val white = Color.WHITE
        val gray = Color.rgb(203, 213, 225)
        val blue = Color.rgb(37, 99, 235)
        val green = Color.rgb(52, 211, 153)

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(backgroundColor)

        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(25), dp(20), dp(30))
        }

        scroll.addView(main)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        heading.addView(text("AUTO DJ", 30f, white, true))
        heading.addView(text("MORPHE · AUTOMATIC MIX", 11f, gray))

        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(text("● ACTIVO", 12f, green, true))
        main.addView(header)

        addSpace(main, 25)

        val currentCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackground(makeBackground(cardColor, 18))
        }

        currentCard.addView(text("REPRODUCIENDO AHORA", 12f, green, true))
        addSpace(currentCard, 15)

        currentSongText = text("Buscando canción...", 23f, white, true)
        currentCard.addView(currentSongText)

        addSpace(currentCard, 5)

        artistText = text("Esperando artista", 14f, gray)
        currentCard.addView(artistText)

        addSpace(currentCard, 20)

        statusText = text("Esperando conexión con YouTube Music", 12f, gray)
        currentCard.addView(statusText)
        main.addView(currentCard)

        addSpace(main, 22)

        val transitionCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackground(makeBackground(cardColor, 16))
        }

        val transitionLabel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        transitionLabel.addView(text("MEZCLA AUTOMÁTICA", 15f, white, true))
        transitionLabel.addView(text("Transición gradual de volumen", 12f, gray))

        transitionCard.addView(
            transitionLabel,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val autoSwitch = Switch(this).apply {
            isChecked = true
        }

        transitionCard.addView(autoSwitch)
        main.addView(transitionCard)

        addSpace(main, 28)
        main.addView(text("PRÓXIMAS CANCIONES", 17f, white, true))
        addSpace(main, 12)

        queueLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        main.addView(queueLayout)

        addSpace(main, 18)

        val addButton = Button(this).apply {
            text = "+  AGREGAR CANCIÓN"
            textSize = 14f
            setTextColor(white)
            setBackground(makeBackground(blue, 14))
            setOnClickListener { addSong() }
        }

        main.addView(addButton, LinearLayout.LayoutParams(-1, dp(55)))

        addSpace(main, 20)

        val footer = text("AUTO DJ MORPHE · VERSIÓN 1.0", 11f, gray)
        footer.gravity = Gravity.CENTER
        main.addView(footer)

        setContentView(scroll)
        refreshQueue()
        updateCurrentSong()
    }

    private fun updateCurrentSong() {
        val preferences = getSharedPreferences(
            "AutoDJMusic",
            MODE_PRIVATE
        )

        val title = preferences.getString("music_title", null)
        val artist = preferences.getString("music_artist", "")

        if (!title.isNullOrBlank()) {
            currentSongText.text = title
            artistText.text = artist?.ifBlank { "Artista no disponible" }
                ?: "Artista no disponible"
            statusText.text = "Conectado con YouTube Music"
        } else {
            currentSongText.text = "No se detecta ninguna canción"
            artistText.text = "Reproduce música en YouTube Music"
            statusText.text = "Esperando información musical"
        }
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(updateTask)
        handler.post(updateTask)
    }

    override fun onPause() {
        handler.removeCallbacks(updateTask)
        super.onPause()
    }

    private fun refreshQueue() {
        queueLayout.removeAllViews()

        if (songs.isEmpty()) {
            queueLayout.addView(
                text("Tu lista está vacía. Agrega canciones.", 13f, Color.LTGRAY)
            )
            return
        }

        songs.forEachIndexed { index, song ->
            val item = TextView(this).apply {
                text = "${index + 1}     ♫     $song"
                textSize = 15f
                setTextColor(Color.WHITE)
                setPadding(dp(15), dp(18), dp(15), dp(18))
                setBackground(makeBackground(Color.rgb(30, 41, 59), 12))
            }

            val params = LinearLayout.LayoutParams(-1, -2)
            params.bottomMargin = dp(9)
            queueLayout.addView(item, params)
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
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    songs.add(name)
                    refreshQueue()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
