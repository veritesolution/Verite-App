package com.example.myapplication.util

import android.widget.ImageView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import coil.transform.CircleCropTransformation
import com.example.myapplication.R
import com.example.myapplication.data.local.AppDatabase
import kotlinx.coroutines.launch
import java.io.File

object ProfileIconHelper {
    /**
     * Synchronizes the profile icon (ImageView) with the user's current profile image
     * from the database, handling the lifecycle of the provided LifecycleOwner.
     */
    fun syncProfileIcon(lifecycleOwner: LifecycleOwner, profileIcon: ImageView) {
        val database = AppDatabase.getDatabase(profileIcon.context.applicationContext)
        
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                database.userDao().getUser().collect { user ->
                    user?.profileImagePath?.let { path ->
                        val file = File(path)
                        if (file.exists()) {
                            profileIcon.clearColorFilter()
                            profileIcon.load(file) {
                                transformations(CircleCropTransformation())
                                placeholder(R.drawable.user)
                                error(R.drawable.user)
                            }
                        } else {
                            profileIcon.setImageResource(R.drawable.user)
                        }
                    } ?: run {
                        profileIcon.setImageResource(R.drawable.user)
                    }
                }
            }
        }
    }
}
