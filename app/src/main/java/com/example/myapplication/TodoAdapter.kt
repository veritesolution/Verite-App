package com.example.myapplication

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.data.model.TodoItem

class TodoAdapter(
    private val onToggle: (TodoItem) -> Unit,
    private val onDelete: (TodoItem) -> Unit
) : ListAdapter<TodoItem, TodoAdapter.TodoViewHolder>(TodoDiffCallback()) {

    class TodoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkbox: CheckBox = view.findViewById(R.id.todoCheckbox)
        val textView: TextView = view.findViewById(R.id.todoText)
        val deleteBtn: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TodoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_todo, parent, false)
        return TodoViewHolder(view)
    }

    override fun onBindViewHolder(holder: TodoViewHolder, position: Int) {
        val item = getItem(position)
        holder.textView.text = item.task
        
        // Prevent recursive listener calls
        holder.checkbox.setOnCheckedChangeListener(null)
        holder.checkbox.isChecked = item.isCompleted
        
        updateStrikeThrough(holder.textView, item.isCompleted)

        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            onToggle(item.copy(isCompleted = isChecked))
        }

        holder.deleteBtn.setOnClickListener {
            onDelete(item)
        }
    }

    private fun updateStrikeThrough(textView: TextView, isCompleted: Boolean) {
        if (isCompleted) {
            textView.paintFlags = textView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            textView.alpha = 0.5f
        } else {
            textView.paintFlags = textView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            textView.alpha = 1.0f
        }
    }

    class TodoDiffCallback : DiffUtil.ItemCallback<TodoItem>() {
        override fun areItemsTheSame(oldItem: TodoItem, newItem: TodoItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TodoItem, newItem: TodoItem): Boolean {
            return oldItem == newItem
        }
    }
}
