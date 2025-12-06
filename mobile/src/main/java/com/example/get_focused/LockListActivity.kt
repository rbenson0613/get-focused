package com.example.get_focused

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LockListActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_placeholder)
        findViewById<TextView>(R.id.placeholder_text).text = "Scheduled Lock Times List"
    }
}
