package com.autodjmorp.app

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("queue", MODE_PRIVATE) }
    private val tracks = mutableListOf<String>()
    private lateinit var listText: TextView
    private lateinit var input: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadQueue()
        handleIncomingShare(intent)
        buildScreen()
        refreshList()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShare(intent)
        if (::listText.isInitialized) refreshList()
    }

    private fun handleIncomingShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val shared = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
            if (!shared.isNullOrBlank()) {
                val url = Regex("https?://\\S+").find(shared)?.value
                if (url != null && !tracks.contains(url)) {
                    tracks.add(url)
                    saveQueue()
                    Toast.makeText(this, "Canción añadida a AutoDJ", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(0xFF10131B.toInt())
        }

        val title = TextView(this).apply {
            text = "AutoDJ MORPHE"
            textSize = 25f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        root.addView(title, matchWidth())

        val subtitle = TextView(this).apply {
            text = "Tu lista de reproducción auxiliar"
            textSize = 14f
            setTextColor(0xFFB8C0D4.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 6, 0, 22)
        }
        root.addView(subtitle, matchWidth())

        input = EditText(this).apply {
            hint = "Pega el enlace de una canción"
            setSingleLine(true)
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF9AA3B8.toInt())
            setPadding(14, 10, 14, 10)
            setBackgroundColor(0xFF252B3A.toInt())
        }
        root.addView(input, matchWidth())

        root.addView(makeButton("AÑADIR A LA COLA") {
            addTrack(input.text.toString())
            input.text.clear()
        })
        root.addView(makeButton("ABRIR PRIMERA CANCIÓN EN MORPHE") {
            openFirst()
        })
        root.addView(makeButton("QUITAR PRIMERA DE LA COLA") {
            if (tracks.isNotEmpty()) {
                tracks.removeAt(0)
                saveQueue()
                refreshList()
            }
        })

        val heading = TextView(this).apply {
            text = "PRÓXIMAS CANCIONES"
            textSize = 14f
            setTextColor(0xFF8EA5FF.toInt())
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 22, 0, 8)
        }
        root.addView(heading, matchWidth())

        listText = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFFE8EAF0.toInt())
            setPadding(12, 12, 12, 12)
            setBackgroundColor(0xFF1B2130.toInt())
        }
        root.addView(listText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val note = TextView(this).apply {
            text = "Nota: esta primera versión organiza enlaces y los abre en Morphe. Aún no mezcla audio ni cambia de canción automáticamente."
            textSize = 12f
            setTextColor(0xFFADB5C7.toInt())
            setPadding(2, 14, 2, 0)
        }
        root.addView(note, matchWidth())
        setContentView(root)
    }

    private fun addTrack(raw: String) {
        val value = raw.trim()
        if (value.isEmpty()) {
            Toast.makeText(this, "Pega primero un enlace", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = runCatching { Uri.parse(value) }.getOrNull()
        if (uri == null || (uri.scheme != "https" && uri.scheme != "http")) {
            Toast.makeText(this, "El enlace no parece válido", Toast.LENGTH_SHORT).show()
            return
        }
        if (!tracks.contains(value)) {
            tracks.add(value)
            saveQueue()
            refreshList()
            Toast.makeText(this, "Añadida a la cola", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Ese enlace ya está en la cola", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFirst() {
        if (tracks.isEmpty()) {
            Toast.makeText(this, "La cola está vacía", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(tracks.first()))
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "No se pudo abrir el enlace", Toast.LENGTH_LONG).show()
        }
    }

    private fun makeButton(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
        }
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun loadQueue() {
        val raw = prefs.getString("items", "[]") ?: "[]"
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) tracks.add(array.getString(i))
        }
    }

    private fun saveQueue() {
        val array = JSONArray()
        tracks.forEach { array.put(it) }
        prefs.edit().putString("items", array.toString()).apply()
    }

    private fun refreshList() {
        if (tracks.isEmpty()) {
            listText.text = "Todavía no has añadido canciones.\n\nEn YouTube Music Morphe, usa Compartir y elige AutoDJ Morphe, o pega aquí el enlace."
        } else {
            listText.text = tracks.mapIndexed { index, url ->
                "${index + 1}. ${url.take(70)}${if (url.length > 70) "…" else ""}"
            }.joinToString("\n\n")
        }
    }
}
