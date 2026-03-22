package com.example.myapplication.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.R
import com.example.myapplication.data.model.VibrationMode
import com.example.myapplication.ui.components.VeriteTopBar
import com.example.myapplication.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    deviceName: String,
    onBackClick: () -> Unit,
    onNavigateToAutoPowerOff: () -> Unit,
    onNavigateToHelpFeedback: () -> Unit,
    onNavigateToQuickStart: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var temperature by remember { mutableStateOf(24) }
    var selectedVibrationMode by remember { mutableStateOf(VibrationMode.OFF) }
    var thermalSensorsEnabled by remember { mutableStateOf(true) }
    var pressureSensorsEnabled by remember { mutableStateOf(true) }
    var vibrationMotorsEnabled by remember { mutableStateOf(true) }
    var showVibrationModes by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    
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
            // Using a custom layout for the title to match the design
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "V ",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp
                        )
                        Text(
                            text = "é",
                            color = AccentPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp
                        )
                        Text(
                            text = " r i t é",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AccentPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /* Profile */ }) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
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
            
            // Device Image
            val imageRes = if (deviceName.contains("Backrest", ignoreCase = true)) {
                R.drawable.smart_backrest
            } else {
                R.drawable.headband
            }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = imageRes),
                    contentDescription = deviceName,
                    modifier = Modifier.size(200.dp, 140.dp),
                    contentScale = ContentScale.Fit
                )
            }
            
            // Device Name Label
            val cleanName = deviceName.removePrefix("Vérité ").removePrefix("Verite ").trim()
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "V",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
                Text(
                    text = "é",
                    color = AccentPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
                Text(
                    text = "rit",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
                Text(
                    text = "é ",
                    color = AccentPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
                Text(
                    text = " $cleanName",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            if (!showSettings) {
                // Vibration Modes Section
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = NodeBgInactive
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showVibrationModes = !showVibrationModes },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Vibration Modes",
                                color = TextPrimary,
                                fontSize = 16.sp
                            )
                            Icon(
                                imageVector = if (showVibrationModes) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = TextPrimary
                            )
                        }
                        
                        // Design shows three vertical dividers/sections below
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color.White.copy(alpha = 0.05f)))
                            VerticalDivider(modifier = Modifier.width(2.dp).fillMaxHeight(), color = Color.White)
                            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color.White.copy(alpha = 0.05f)))
                            VerticalDivider(modifier = Modifier.width(2.dp).fillMaxHeight(), color = Color.White)
                            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color.White.copy(alpha = 0.05f)))
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Temperature Control
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = NodeBgInactive
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Temperature Adjustment",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                        
                        VerticalDivider(modifier = Modifier.height(40.dp).width(2.dp), color = Color.White)
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 16.dp)
                        ) {
                            IconButton(onClick = { if (temperature > 15) temperature-- }) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Decrease",
                                    tint = Color(0xFF64B5F6),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.1f),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "$temperature",
                                        color = TextPrimary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            
                            IconButton(onClick = { if (temperature < 40) temperature++ }) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Increase",
                                    tint = Color(0xFFEF9A9A),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f).height(80.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = NodeBgInactive,
                        onClick = { /* Cooling Flow */ }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text("Cooling Flow", color = TextPrimary, fontSize = 14.sp)
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextPrimary)
                        }
                    }
                    
                    Surface(
                        modifier = Modifier.weight(1f).height(80.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = NodeBgInactive,
                        onClick = { showSettings = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text("More Settings", color = TextPrimary, fontSize = 14.sp)
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextPrimary)
                        }
                    }
                }
            } else {
                // Settings Section (Keep existing or refine to match style)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SensorToggle(
                        label = "Thermal Sensors",
                        checked = thermalSensorsEnabled,
                        onCheckedChange = { thermalSensorsEnabled = it }
                    )
                    
                    SensorToggle(
                        label = "Pressure Sensors",
                        checked = pressureSensorsEnabled,
                        onCheckedChange = { pressureSensorsEnabled = it }
                    )
                    
                    SensorToggle(
                        label = "Vibration Motors",
                        checked = vibrationMotorsEnabled,
                        onCheckedChange = { vibrationMotorsEnabled = it }
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    SettingsNavigationItem(
                        label = "Auto Power Off",
                        onClick = onNavigateToAutoPowerOff
                    )
                    
                    SettingsNavigationItem(
                        label = "Help & Feedback",
                        onClick = onNavigateToHelpFeedback
                    )
                    
                    SettingsNavigationItem(
                        label = "Quick Start Guide",
                        onClick = onNavigateToQuickStart
                    )
                    
                    Button(
                        onClick = { showSettings = false },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NodeBgInactive),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Changes", color = TextPrimary)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
}

@Composable
private fun SensorToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = NodeBgInactive
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AccentPrimary,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = DividerColor
                )
            )
        }
    }
}

@Composable
private fun SettingsNavigationItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = NodeBgInactive,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Navigate",
                tint = TextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
