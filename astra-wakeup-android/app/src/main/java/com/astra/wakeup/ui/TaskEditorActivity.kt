package com.astra.wakeup.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.astra.wakeup.R
import com.astra.wakeup.brain.actions.Action
import com.astra.wakeup.brain.tasks.Task
import com.astra.wakeup.brain.tasks.TaskStep
import com.astra.wakeup.brain.tasks.TaskStorage

class TaskEditorActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_editor)

        val etId = findViewById<EditText>(R.id.etTaskId)
        val etDesc = findViewById<EditText>(R.id.etTaskDesc)
        val sp = findViewById<Spinner>(R.id.spTaskTrigger)
        val etSpeech = findViewById<EditText>(R.id.etTaskSpeech)
        val tv = findViewById<TextView>(R.id.tvTasks)

        fun refresh() {
            val tasks = TaskStorage.list(this)
            tv.text = if (tasks.isEmpty()) "No custom tasks" else tasks.joinToString("\n\n") {
                "${it.id} (${it.trigger}) -> ${it.description}"
            }
        }

        findViewById<Button>(R.id.btnSaveTask).setOnClickListener {
            val id = etId.text.toString().trim()
            val desc = etDesc.text.toString().trim()
            val trig = sp.selectedItem.toString()
            val speech = etSpeech.text.toString().trim()
            if (id.isBlank() || speech.isBlank()) {
                Toast.makeText(this, "Task ID + speech required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val existing = TaskStorage.list(this).filterNot { it.id == id }.toMutableList()
            existing += Task(
                id = id,
                description = if (desc.isBlank()) id else desc,
                trigger = trig,
                steps = listOf(TaskStep("speak", Action.Speak(speech))),
                priority = 60
            )
            TaskStorage.save(this, existing)
            Toast.makeText(this, "Task saved", Toast.LENGTH_SHORT).show()
            refresh()
        }

        refresh()
    }
}
