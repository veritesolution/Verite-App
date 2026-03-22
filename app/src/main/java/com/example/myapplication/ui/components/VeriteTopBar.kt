package com.example.myapplication.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.R
import com.example.myapplication.ui.theme.AccentPrimary
import com.example.myapplication.ui.theme.TextPrimary
import com.example.myapplication.ui.theme.cormorantFamily
import com.example.myapplication.ui.theme.outfitFamily
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle

@Composable
fun VeriteTopBar(
    onBackClick: () -> Unit,
    onProfileClick: () -> Unit = {},
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

        // Right Section: Profile Icon
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(40.dp)
                .background(Color(0xFF0F1B1B), CircleShape)
                .border(1.dp, Color(0xFF1C9C91), CircleShape)
                .clip(CircleShape)
                .clickable { onProfileClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_account),
                contentDescription = "Profile",
                modifier = Modifier.size(24.dp),
                tint = Color(180, 180, 180) // Grey icon as requested
            )
        }
    }
}
