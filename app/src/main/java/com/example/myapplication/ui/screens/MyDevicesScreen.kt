package com.example.myapplication.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.R
import com.example.myapplication.ui.components.BottomNavBar
import com.example.myapplication.ui.components.DeviceCard
import com.example.myapplication.ui.components.FloatingVoiceAssistant
import com.example.myapplication.ui.components.VeriteTopBar
import com.example.myapplication.ui.home.SkyBackground
import com.example.myapplication.ui.theme.*
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.myapplication.viewmodel.DeviceViewModel

@Composable
fun MyDevicesScreen(
    viewModel: DeviceViewModel,
    onBackClick: () -> Unit,
    onProfileClick: () -> Unit = {},
    onNavigateToAutoPowerOff: () -> Unit,
    onNavigateToHelpFeedback: () -> Unit,
    onNavigateToQuickStart: () -> Unit = {},
    onDeviceClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val devices by viewModel.devices.collectAsState()
    var selectedNavItem by remember { mutableStateOf(0) }
    
    SkyBackground {
        Scaffold(
            topBar = {
                VeriteTopBar(onBackClick = onBackClick, onProfileClick = onProfileClick)
            },
            bottomBar = {
                BottomNavBar(
                    selectedItem = selectedNavItem,
                    onItemSelected = { selectedNavItem = it }
                )
            },
            containerColor = Color.Transparent,
            floatingActionButton = {
                FloatingVoiceAssistant(
                    onVoiceCommand = { command ->
                        // Process command with advanced pattern matching
                        val result = com.example.myapplication.utils.VoiceCommandProcessor.  processCommand(command)
                        
                        when (result.action) {
                            com.example.myapplication.utils.VoiceCommandProcessor.CommandAction.NAVIGATE_AUTO_POWER_OFF -> {
                                onNavigateToAutoPowerOff()
                            }
                            com.example.myapplication.utils.VoiceCommandProcessor.CommandAction.NAVIGATE_HELP -> {
                                onNavigateToHelpFeedback()
                            }
                            com.example.myapplication.utils.VoiceCommandProcessor.CommandAction.NAVIGATE_QUICK_START -> {
                                onNavigateToQuickStart()
                            }
                            com.example.myapplication.utils.VoiceCommandProcessor.CommandAction.NAVIGATE_DEVICES -> {
                                // Already on devices screen
                            }
                            com.example.myapplication.utils.VoiceCommandProcessor.CommandAction.SET_TEMPERATURE -> {
                                // Navigate to first device detail
                                if (devices.isNotEmpty()) {
                                    onDeviceClick(devices[0].name)
                                }
                            }
                            com.example.myapplication.utils.VoiceCommandProcessor.CommandAction.TOGGLE_VIBRATION -> {
                                // Navigate to first device detail
                                if (devices.isNotEmpty()) {
                                    onDeviceClick(devices[0].name)
                                }
                            }
                            else -> {
                                // Unknown command - do nothing
                            }
                        }
                    }
                )
            },
            floatingActionButtonPosition = FabPosition.End
        ) { paddingValues ->
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.my_devices),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentPrimary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Glass Container for Devices
                Surface(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(31.dp),
                    color = Color(0xFF2F4949).copy(alpha = 0.30f)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                    items(devices) { device ->
                        DeviceCard(
                            device = device,
                            onClick = {
                                onDeviceClick(device.name)
                            }
                        )
                    }
                    
                            // Add some spacing at the bottom
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }
        }
}

@Composable
private fun SettingsMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentPrimary,
                modifier = Modifier.size(24.dp)
            )
            
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
        }
        
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Navigate",
            tint = AccentSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}
