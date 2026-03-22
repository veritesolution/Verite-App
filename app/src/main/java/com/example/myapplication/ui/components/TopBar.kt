package com.example.myapplication.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.AccentPrimary
import com.example.myapplication.ui.theme.cormorantFamily
import com.example.myapplication.ui.theme.outfitFamily

@Composable
fun TopBar(
    title: String? = null,
    onBackClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Back Button
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                modifier = Modifier.size(24.dp),
                tint = Color(200, 220, 215, (0.4 * 255).toInt())
            )
        }

        // Title
        if (title != null) {
            Text(
                modifier = Modifier.align(Alignment.Center),
                text = title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            Text(
                modifier = Modifier.align(Alignment.Center),
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
        }

        // Profile Icon with larger hit target
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(64.dp) // Larger hit area
                .clickable { onProfileClick() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(41.dp) // Original visual size
                    .background(Color(0xFF0F1B1B), CircleShape)
                    .border(1.dp, Color(0xFF1C9C91), CircleShape)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(id = com.example.myapplication.R.drawable.ic_account),
                    contentDescription = "Profile",
                    modifier = Modifier.size(24.dp),
                    tint = Color(180, 180, 180) // Grey icon
                )
            }
        }
    }
}
