package com.example.get_focused

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class AddSelectionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_selection)

        findViewById<MaterialCardView>(R.id.card_schedule_event).setOnClickListener {
            startActivity(Intent(this, ScheduleEventActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.card_schedule_lock).setOnClickListener {
            startActivity(Intent(this, ScheduleLockActivity::class.java))
        }
    }
}
