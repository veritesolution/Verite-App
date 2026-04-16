package com.example.myapplication

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.model.User
import com.example.myapplication.ui.components.TopBar
import com.example.myapplication.ui.components.VeriteAlert
import com.example.myapplication.ui.home.SkyBackground
import com.example.myapplication.ui.theme.VeriteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class EditProfileActivity : ComponentActivity() {

    private var selectedImageUri by mutableStateOf<Uri?>(null)

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            selectedImageUri = uri
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            VeriteTheme {
                SkyBackground {
                    EditProfileScreen(
                        onBackClick = { finish() },
                        onPickImage = {
                            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        selectedImageUri = selectedImageUri,
                        onSave = { name, email, newUri, currentUser ->
                            saveProfile(name, email, newUri, currentUser)
                        }
                    )
                }
            }
        }
    }

    private fun saveProfile(name: String, email: String, newUri: Uri?, currentUser: User?) {
        if (name.isBlank() || email.isBlank()) {
            VeriteAlert.warning(this, "Name and Email are required")
            return
        }
        
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            var imagePath = currentUser?.profileImagePath

            newUri?.let { uri ->
                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    val fileName = "profile_${System.currentTimeMillis()}.jpg"
                    val file = File(filesDir, fileName)
                    val outputStream = FileOutputStream(file)
                    
                    inputStream?.copyTo(outputStream)
                    inputStream?.close()
                    outputStream.close()
                    
                    if (!imagePath.isNullOrEmpty() && imagePath != file.absolutePath) {
                        val oldFile = File(imagePath!!)
                        if (oldFile.exists()) oldFile.delete()
                    }
                    imagePath = file.absolutePath
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val updatedUser = User(
                id = 1, // Updating the singular local user
                name = name,
                email = email,
                profileImagePath = imagePath,
                joinDate = currentUser?.joinDate ?: System.currentTimeMillis()
            )

            db.userDao().insertUser(updatedUser)

            withContext(Dispatchers.Main) {
                VeriteAlert.success(this@EditProfileActivity, "Profile Updated")
                finish()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onBackClick: () -> Unit,
    onPickImage: () -> Unit,
    selectedImageUri: Uri?,
    onSave: (String, String, Uri?, User?) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var currentUser by remember { mutableStateOf<User?>(null) }
    var initialLoadDone by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val db = AppDatabase.getDatabase(context)
        db.userDao().getUser().collect { user ->
            if (!initialLoadDone) {
                currentUser = user
                if (user != null) {
                    name = user.name
                    email = user.email
                } else {
                    name = "User"
                    email = "user@example.com"
                }
                initialLoadDone = true
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            onBackClick = onBackClick,
            onProfileClick = { /* No-op on account screen */ }
        )

        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Edit Profile",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Profile Picture
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray)
                    .clickable { onPickImage() },
                contentAlignment = Alignment.Center
            ) {
                val imageModel = selectedImageUri 
                    ?: currentUser?.profileImagePath?.let { File(it) } 
                    ?: R.drawable.user

                AsyncImage(
                    model = imageModel,
                    contentDescription = "Profile Picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Change Photo",
                color = Color(0xFF00FFB2),
                fontSize = 16.sp,
                modifier = Modifier.clickable { onPickImage() }
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Name Field
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full Name", color = Color(0xFFB0BEC5)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00FFB2),
                    unfocusedBorderColor = Color(0xFF00FFB2).copy(alpha = 0.5f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFF00FFB2)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Email Field
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address", color = Color(0xFFB0BEC5)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00FFB2),
                    unfocusedBorderColor = Color(0xFF00FFB2).copy(alpha = 0.5f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFF00FFB2)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = { onSave(name, email, selectedImageUri, currentUser) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFB2)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Save Changes",
                    color = Color.Black,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
