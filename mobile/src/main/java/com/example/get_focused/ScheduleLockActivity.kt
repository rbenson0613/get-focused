package com.example.get_focused

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class ScheduleLockActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule_lock)

        val inputName = findViewById<EditText>(R.id.input_lock_name)
        val inputDate = findViewById<EditText>(R.id.input_lock_date)
        val inputStartTime = findViewById<EditText>(R.id.input_start_time)
        val inputEndTime = findViewById<EditText>(R.id.input_end_time)
        val switchRepeat = findViewById<SwitchCompat>(R.id.switch_repeat)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btn_create_lock).setOnClickListener {
            val title = inputName.text.toString()
            val date = inputDate.text.toString()
            val start = inputStartTime.text.toString()
            val end = inputEndTime.text.toString()
            val isRepeating = switchRepeat.isChecked

            if (title.isNotEmpty()) {
                EventManager.addLock(title, date, start, end, isRepeating)
            }
            finish()
        }
    }
}
