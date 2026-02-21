package com.transad.app

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tvHello = findViewById<TextView>(R.id.tvHello)
        val btnClick = findViewById<Button>(R.id.btnClick)

        btnClick.setOnClickListener {
            Toast.makeText(this, "¡Botón presionado!", Toast.LENGTH_SHORT).show()
            tvHello.text = "¡Has hecho clic!"
        }
    }
}
