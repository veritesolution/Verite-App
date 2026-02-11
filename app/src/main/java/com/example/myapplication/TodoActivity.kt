package com.example.myapplication

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.model.TodoItem
import kotlinx.coroutines.launch

class TodoActivity : AppCompatActivity() {

    private lateinit var adapter: TodoAdapter
    private val database by lazy { AppDatabase.getDatabase(this) }
    private val todoDao by lazy { database.todoDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_todo)

        setupUI()
        observeTasks()
    }

    private fun setupUI() {
        findViewById<ImageView>(R.id.backButton).setOnClickListener {
            finish()
        }

        val recyclerView = findViewById<RecyclerView>(R.id.todoRecyclerView)
        adapter = TodoAdapter(
            onToggle = { item ->
                lifecycleScope.launch {
                    todoDao.updateTask(item)
                }
            },
            onDelete = { item ->
                lifecycleScope.launch {
                    todoDao.deleteTask(item)
                }
            }
        )
        recyclerView.adapter = adapter

        val inputTask = findViewById<EditText>(R.id.inputTask)
        val btnAdd = findViewById<ImageButton>(R.id.btnAddTodo)

        val addTaskAction = {
            val taskText = inputTask.text.toString().trim()
            if (taskText.isNotEmpty()) {
                lifecycleScope.launch {
                    todoDao.insertTask(TodoItem(task = taskText))
                    inputTask.text.clear()
                }
            }
        }

        btnAdd.setOnClickListener { addTaskAction() }
        
        inputTask.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addTaskAction()
                true
            } else {
                false
            }
        }
    }

    private fun observeTasks() {
        lifecycleScope.launch {
            todoDao.getAllTasks().collect { tasks ->
                adapter.submitList(tasks)
            }
        }
    }
}
