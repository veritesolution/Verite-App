package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.components.VeriteTopBar
import com.example.myapplication.ui.theme.*

@Composable
fun PrivacyScreen(
    onBackClick: () -> Unit,
    onNavigateToDashboard: () -> Unit
) {
    var agreed by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            VeriteTopBar(onBackClick = onBackClick)
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = CardBackground.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                Text(
                    text = "Bad Addiction & Bad habits",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp),
                color = CardBackground.copy(alpha = 0.3f),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TealPrimary.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        text = "Privacy",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = "Last Updated: [1.1]",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "We respect your privacy and are committed to protecting your personal information. This Privacy Policy explains how we collect, use, and safeguard the information you provide in your Profile section.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "1. Information We Collect",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = "When you create or update your profile, we may collect:\n• Name, email address, and profile photo\n• Contact details you choose to share\n• Other optional information (e.g., bio, preferences)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "2. How We Use Your Information",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = "Your information is used to:\n• Display your profile details to you and, if applicable, to other users\n• Improve our services and personalize your experience\n• Communicate important updates about your account or our products",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                color = CardBackground,
                shape = RoundedCornerShape(30.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Agreed",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Checkbox(
                        checked = agreed,
                        onCheckedChange = { agreed = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = TealPrimary,
                            uncheckedColor = TextSecondary,
                            checkmarkColor = DarkBackground
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (agreed) {
                Button(
                    onClick = onNavigateToDashboard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                    shape = RoundedCornerShape(30.dp)
                ) {
                    Text(
                        text = "Continue",
                        color = DarkBackground,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
