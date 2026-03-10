package com.astra.wakeup.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.astra.wakeup.R
import com.astra.wakeup.alarm.AlarmScheduler

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnSchedule).setOnClickListener {
            AlarmScheduler.scheduleDaily(this, 5, 50)
            Toast.makeText(this, "Scheduled for 5:50 AM ET", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnTest).setOnClickListener {
            startActivity(Intent(this, WakeActivity::class.java))
        }
    }
}
