package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.R
import com.example.myapplication.ui.components.VeriteTopBar
import com.example.myapplication.ui.theme.*

@Composable
fun QuickStartGuideScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .paint(
                painter = painterResource(id = R.drawable.group_1000006461),
                contentScale = ContentScale.FillBounds
            )
    ) {
        Scaffold(
            topBar = {
                VeriteTopBar(onBackClick = onBackClick)
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Quick Start Guide",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TealPrimary
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            GuideStep(
                number = 1,
                title = "Connect Your Device",
                description = "Ensure your Bluetooth is on and tap a device card on the home screen to connect.",
                icon = Icons.Default.Bluetooth
            )
            
            GuideStep(
                number = 2,
                title = "Voice Control",
                description = "Tap the floating mic icon to use voice commands like 'power off' or 'help'.",
                icon = Icons.Default.Mic
            )
            
            GuideStep(
                number = 3,
                title = "Adjust Comfort",
                description = "In the device detail screen, you can adjust temperature and vibration modes.",
                icon = Icons.Default.AutoAwesome
            )
            
            GuideStep(
                number = 4,
                title = "Smart Savings",
                description = "Set an Auto Power Off timer to conserve battery when not in use.",
                icon = Icons.Default.PowerSettingsNew
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = onBackClick,
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Got it!", color = DarkBackground, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
}

@Composable
private fun GuideStep(
    number: Int,
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        shape = RoundedCornerShape(20.dp),
        color = CardBackground
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(TealPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = TealPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Text(
                    text = "Step $number: $title",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = TextSecondary,
                    lineHeight = 20.sp
                )
            }
        }
    }
}
