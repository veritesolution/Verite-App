package com.example.myapplication.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.R
import com.example.myapplication.ui.components.TopBar
import com.example.myapplication.ui.home.SkyBackground
import com.example.myapplication.ui.theme.*

@Composable
fun ProfileScreen(
    userName: String,
    userEmail: String,
    profileImagePath: String?,
    isWakeWordEnabled: Boolean,
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    onAccount: () -> Unit,
    onAppearance: () -> Unit,
    onLanguage: () -> Unit,
    onNotifications: () -> Unit,
    onPrivacy: () -> Unit,
    onVoiceAgent: () -> Unit,
    onWakeWordToggle: (Boolean) -> Unit,
    onLogout: () -> Unit
) {
    SkyBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_back),
                        contentDescription = "Back",
                        tint = AccentPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                // Vérité Logo
                Text(
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(
                            fontFamily = cormorantFamily,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.W500
                        )) { append("V") }
                        withStyle(style = SpanStyle(
                            fontFamily = outfitFamily,
                            fontWeight = FontWeight.W400
                        )) { append("érit") }
                        withStyle(style = SpanStyle(
                            fontFamily = cormorantFamily,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.W500,
                            color = AccentPrimary
                        )) { append("é") }
                    },
                    color = Color.White,
                    fontSize = 24.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                // Invisible spacer for balance
                Spacer(modifier = Modifier.size(48.dp))
            }

            // Orbital Avatar
            Box(
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 24.dp)
                    .size(160.dp),
                contentAlignment = Alignment.Center
            ) {
                // Rotating Ring
                val infiniteTransition = rememberInfiniteTransition(label = "ring_rotation")
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(12000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "rotation_anim"
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(rotation)
                        .border(
                            width = 1.dp,
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color.Transparent, 
                                    AccentPrimary.copy(alpha = 0.3f),
                                    AccentPrimary,
                                    AccentPrimary.copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )

                // Avatar Image
                val avatarModifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.White.copy(0.1f), CircleShape)
                    .background(Color(0xFF0D1817))

                val bitmap by remember(profileImagePath) {
                    mutableStateOf(
                        try {
                            if (!profileImagePath.isNullOrEmpty()) {
                                BitmapFactory.decodeFile(profileImagePath)?.asImageBitmap()
                            } else null
                        } catch (e: Exception) {
                            null
                        }
                    )
                }

                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!,
                        contentDescription = "Profile Picture",
                        modifier = avatarModifier,
                        contentScale = ContentScale.Crop
                    )
                } else {
                    DefaultAvatar(avatarModifier)
                }

                // Edit Button
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 12.dp, end = 12.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AccentPrimary.copy(alpha = 0.2f))
                        .border(1.dp, AccentPrimary, CircleShape)
                        .clickable { onEditProfile() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_edit),
                        contentDescription = "Edit Profile",
                        tint = AccentPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // User Info
            Text(
                text = userName,
                color = TextPrimary,
                fontSize = 26.sp,
                fontFamily = outfitFamily,
                fontWeight = FontWeight.W300,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Text(
                text = userEmail,
                color = TextUltraFaint,
                fontSize = 14.sp,
                fontFamily = outfitFamily,
                fontWeight = FontWeight.W200,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Premium Badge
            Box(
                modifier = Modifier
                    .border(1.dp, AccentPrimary.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .background(AccentPrimary.copy(alpha = 0.1f))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "[ PREMIUM MEMBER ]",
                    color = AccentPrimary,
                    fontSize = 10.sp,
                    fontFamily = outfitFamily,
                    fontWeight = FontWeight.W400,
                    letterSpacing = 2.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Settings Header
            Text(
                text = "SYS.CFG // SETTINGS",
                color = AccentPrimary.copy(alpha = 0.7f),
                fontSize = 10.sp,
                fontFamily = outfitFamily,
                fontWeight = FontWeight.W400,
                letterSpacing = 3.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp, end = 32.dp, bottom = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Start
            )

            // Settings List
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GlassCardRow("Account", R.drawable.ic_account, onClick = onAccount)
                GlassCardRow("Appearance", R.drawable.ic_appearance, onClick = onAppearance)
                GlassCardRow("Language", R.drawable.ic_language, onClick = onLanguage)
                GlassCardRow("Notifications", R.drawable.ic_notifications, onClick = onNotifications)
                GlassCardRow("Privacy & Security", R.drawable.ic_privacy, onClick = onPrivacy)
                GlassCardRow("Voice Agent", R.drawable.ic_sound, onClick = onVoiceAgent)
                
                GlassCardSwitchRow(
                    title = "Wake-Word Listener", 
                    icon = R.drawable.ic_sound, 
                    isChecked = isWakeWordEnabled,
                    onCheckedChange = onWakeWordToggle
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Terminate Session Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0x33FF1744),
                                Color(0x1AFF1744)
                            )
                        )
                    )
                    .border(1.dp, Color(0xFFFF1744), RoundedCornerShape(8.dp))
                    .clickable { onLogout() }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "TERMINATE SESSION",
                    color = Color(0xFFFF5252),
                    fontSize = 14.sp,
                    fontFamily = outfitFamily,
                    fontWeight = FontWeight.W600,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

@Composable
fun DefaultAvatar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.user),
            contentDescription = "Default Profile",
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(48.dp)
        )
    }
}

@Composable
fun GlassCardRow(title: String, icon: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0D1817).copy(alpha = 0.6f))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        // Glowing side strip
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(28.dp)
                .background(AccentPrimary)
                .align(Alignment.CenterStart)
                .offset(x = 12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = title,
                tint = AccentPrimary,
                modifier = Modifier.size(24.dp)
            )
            
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontFamily = outfitFamily,
                fontWeight = FontWeight.W300,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            )

            Text(
                text = ">>",
                color = AccentPrimary.copy(alpha = 0.7f),
                fontSize = 14.sp,
                fontFamily = outfitFamily,
                fontWeight = FontWeight.W600,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
fun GlassCardSwitchRow(title: String, icon: Int, isChecked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0D1817).copy(alpha = 0.6f))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
    ) {
        // Glowing side strip
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(28.dp)
                .background(AccentPrimary.copy(alpha = if (isChecked) 1f else 0.2f))
                .align(Alignment.CenterStart)
                .offset(x = 12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = title,
                tint = AccentPrimary.copy(alpha = if (isChecked) 1f else 0.5f),
                modifier = Modifier.size(24.dp)
            )
            
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontFamily = outfitFamily,
                fontWeight = FontWeight.W300,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            )

            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AccentPrimary,
                    uncheckedThumbColor = Color.White.copy(alpha = 0.5f),
                    uncheckedTrackColor = Color.White.copy(alpha = 0.1f),
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }
    }
}
