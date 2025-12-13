package com.example.get_focused

import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class ScheduleLockActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule_lock)

        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        val btnCancel = findViewById<Button>(R.id.btn_cancel)
        val btnCreate = findViewById<Button>(R.id.btn_create_lock)

        btnBack.setOnClickListener {
            finish()
        }

        btnCancel.setOnClickListener {
            finish()
        }

        btnCreate.setOnClickListener {
            // Logic to create lock would go here
            finish()
        }
    }
}
