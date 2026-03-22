package com.example.myapplication

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.CircleCropTransformation
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.model.User
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class EditProfileActivity : AppCompatActivity() {

    private lateinit var etName: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var imageViewProfile: ImageView
    private var selectedImageUri: Uri? = null
    private var currentUser: User? = null

    // Image Picker
    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            imageViewProfile.load(uri) {
                crossfade(true)
                transformations(CircleCropTransformation())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        etName = findViewById(R.id.etName)
        etEmail = findViewById(R.id.etEmail)
        imageViewProfile = findViewById(R.id.imageViewProfile)
        
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnBack = findViewById<android.view.View>(R.id.btnBack)
        val tvChangePhoto = findViewById<TextView>(R.id.tvChangePhoto)
        val profileImageContainer = findViewById<android.view.View>(R.id.profileImageContainer)

        btnBack.setOnClickListener { finish() }

        // Load  existing  data
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            currentUser = db.userDao().getUser().firstOrNull()

            if (currentUser != null) {
                etName.setText(currentUser!!.name)
                etEmail.setText(currentUser!!.email)
                if (!currentUser!!.profileImagePath.isNullOrEmpty()) {
                    val file = File(currentUser!!.profileImagePath!!)
                    if (file.exists()) {
                        imageViewProfile.load(file) {
                            transformations(CircleCropTransformation())
                        }
                    }
                }
            } else {
                // Initialize default user if none exists
                 etName.setText("User")
                 etEmail.setText("user@example.com")
            }
        }

        // Image Picker implementation
        val openPicker = {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        
        tvChangePhoto.setOnClickListener { openPicker() }
        profileImageContainer.setOnClickListener { openPicker() }

        // Save Logic
        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()

            if (name.isEmpty()) {
                etName.error = "Name is required"
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val db = AppDatabase.getDatabase(applicationContext)
                
                var imagePath = currentUser?.profileImagePath

                // Save image to internal storage if a new one is picked
                selectedImageUri?.let { uri ->
                    val inputStream = contentResolver.openInputStream(uri)
                    val fileName = "profile_${System.currentTimeMillis()}.jpg"
                    val file = File(filesDir, fileName)
                    val outputStream = FileOutputStream(file)
                    
                    inputStream?.copyTo(outputStream)
                    inputStream?.close()
                    outputStream.close()
                    
                    // Delete old image if exists
                    if (!imagePath.isNullOrEmpty()) {
                        val oldFile = File(imagePath!!)
                        if (oldFile.exists()) oldFile.delete()
                    }
                    
                    imagePath = file.absolutePath
                }

                val updatedUser = User(
                    id = 1,
                    name = name,
                    email = email,
                    profileImagePath = imagePath,
                    joinDate = currentUser?.joinDate ?: System.currentTimeMillis()
                )

                db.userDao().insertUser(updatedUser)
                
                Toast.makeText(this@EditProfileActivity, "Profile Updated!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
