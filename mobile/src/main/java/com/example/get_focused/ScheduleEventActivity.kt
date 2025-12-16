package com.example.get_focused

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class ScheduleEventActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule_event)

        val inputName = findViewById<EditText>(R.id.input_event_name)
        val inputDate = findViewById<EditText>(R.id.input_event_date) // In a real app, use DatePickerDialog
        val inputStartTime = findViewById<EditText>(R.id.input_start_time) // In a real app, use TimePickerDialog
        val inputDuration = findViewById<EditText>(R.id.input_duration)
        val inputDescription = findViewById<EditText>(R.id.input_description)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btn_create_event).setOnClickListener {
            val title = inputName.text.toString()
            val date = inputDate.text.toString()
            val time = inputStartTime.text.toString()
            val duration = inputDuration.text.toString()
            val description = inputDescription.text.toString()

            // Basic validation
            if (title.isNotEmpty()) {
                EventManager.addEvent(title, date, time, duration, description)
            }
            finish()
        }
    }
}
