package com.timursarsembayev.cubesorter

import android.app.Activity
import android.os.Bundle
import android.widget.Button

class CongratulationsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_congratulations)

        val buttonBackToMenu = findViewById<Button>(R.id.buttonBackToMenu)
        buttonBackToMenu.setOnClickListener {
            finish() // Возвращаемся к главному экрану
        }
    }
}
