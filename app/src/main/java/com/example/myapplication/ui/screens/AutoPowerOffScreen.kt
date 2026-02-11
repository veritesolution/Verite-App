package com.example.myapplication.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.R
import com.example.myapplication.data.model.PowerOffDuration
import com.example.myapplication.ui.components.VeriteButton
import com.example.myapplication.ui.components.VeriteTopBar
import com.example.myapplication.ui.theme.*
import com.example.myapplication.viewmodel.SettingsViewModel

@Composable
fun AutoPowerOffScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsState()
    
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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // Main Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = CardBackground
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.auto_power_off),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Options Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = CardBackgroundDark
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            PowerOffDuration.values().forEach { duration ->
                                PowerOffOption(
                                    text = duration.displayName,
                                    isSelected = settings.selectedDuration == duration,
                                    onClick = { viewModel.updateDuration(duration) }
                                )
                                if (duration != PowerOffDuration.values().last()) {
                                    Divider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        color = DividerColor
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Progress Indicator
                    LinearProgressIndicator(
                        progress = 0.6f,
                        modifier = Modifier
                            .width(200.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = TealPrimary,
                        trackColor = DividerColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Description Text
            Text(
                text = stringResource(R.string.power_off_description),
                fontSize = 14.sp,
                color = TealSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Confirm Button
            VeriteButton(
                text = stringResource(R.string.confirm),
                onClick = onBackClick,
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }
    }
}
}

@Composable
private fun PowerOffOption(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = TextPrimary
        )
        
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.VisibilityOff,
                contentDescription = stringResource(R.string.cd_selection_indicator),
                tint = TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
