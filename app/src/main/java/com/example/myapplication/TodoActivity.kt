package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.ui.todo.TodoMainScreen
import com.example.myapplication.viewmodel.TodoViewModel

class TodoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            val viewModel: TodoViewModel = viewModel()
            TodoMainScreen(
                viewModel = viewModel,
                onBack = { finish() }
            )
        }
    }
}
