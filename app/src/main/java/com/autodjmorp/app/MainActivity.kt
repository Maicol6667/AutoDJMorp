
package com.autodjmorp

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        layout.setPadding(30, 30, 30, 30)
        layout.setBackgroundColor(Color.rgb(18, 18, 28))

        val title = TextView(this)
        title.text = "AUTO DJ MORPHE"
        title.textSize = 30f
        title.setTextColor(Color.WHITE)
        title.gravity = Gravity.CENTER

        val message = TextView(this)
        message.text = "¡Bienvenido!\n\nTu proyecto Auto DJ está instalado."
        message.textSize = 18f
        message.setTextColor(Color.LTGRAY)
        message.gravity = Gravity.CENTER
        message.setPadding(10, 40, 10, 10)

        layout.addView(title)
        layout.addView(message)

        setContentView(layout)
    }
}
