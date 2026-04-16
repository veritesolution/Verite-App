package com.example.myapplication.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.R
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.ui.theme.AccentPrimary
import com.example.myapplication.ui.theme.TextPrimary
import com.example.myapplication.ui.theme.cormorantFamily
import com.example.myapplication.ui.theme.outfitFamily
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import com.example.myapplication.ui.notification.NotificationBell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun VeriteTopBar(
    onBackClick: () -> Unit,
    onProfileClick: () -> Unit = {},
    notificationCount: Int = 0,
    onNotificationClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Center Section: Logo
        Text(
            text = buildAnnotatedString {
                withStyle(style = SpanStyle(
                    fontFamily = cormorantFamily,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.W500,
                    fontSize = 24.sp
                )) {
                    append("V")
                }
                withStyle(style = SpanStyle(
                    fontFamily = outfitFamily,
                    fontWeight = FontWeight.W300,
                    fontSize = 20.sp
                )) {
                    append("érit")
                }
                withStyle(style = SpanStyle(
                    fontFamily = cormorantFamily,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.W500,
                    fontSize = 24.sp,
                    color = AccentPrimary
                )) {
                    append("é")
                }
            },
            color = Color.White
        )

        // Left Section: Back Button
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(48.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back_button),
                tint = Color.White.copy(alpha = 0.7f)
            )
        }

        // Right Section: Notification Bell + Profile Icon
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Notification Bell
            NotificationBell(
                unreadCount = notificationCount,
                onClick = onNotificationClick
            )

            // Profile Icon — loads actual user photo from DB
            ProfileIconCompose(onClick = onProfileClick)
        }
    }
}

/**
 * Profile icon that loads the user's actual profile photo from Room DB.
 * Falls back to a bright, visible default icon if no photo is set.
 */
@Composable
private fun ProfileIconCompose(onClick: () -> Unit) {
    val context = LocalContext.current
    var profileBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    // Load user profile image from database
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val user = db.userDao().getUser().firstOrNull()
                user?.profileImagePath?.let { path ->
                    val file = File(path)
                    if (file.exists()) {
                        profileBitmap = BitmapFactory.decodeFile(path)
                    }
                }
            } catch (_: Exception) { }
        }
    }

    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color(0xFF142824), CircleShape)
            .border(1.5.dp, AccentPrimary, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (profileBitmap != null) {
            Image(
                bitmap = profileBitmap!!.asImageBitmap(),
                contentDescription = "Profile",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            // Bright default icon — clearly visible on dark background
            Icon(
                painter = painterResource(id = R.drawable.ic_account),
                contentDescription = "Profile",
                modifier = Modifier.size(22.dp),
                tint = Color.White
            )
        }
    }
}
