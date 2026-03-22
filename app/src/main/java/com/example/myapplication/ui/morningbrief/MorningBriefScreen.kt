package com.example.myapplication.ui.morningbrief

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.myapplication.R
import com.example.myapplication.data.model.Task
import com.example.myapplication.ui.theme.AccentPrimary
import com.example.myapplication.ui.theme.NodeBgInactive
import com.example.myapplication.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorningBriefScreen(
    viewModel: MorningBriefViewModel,
    onBack: () -> Unit,
    onVoiceAssistant: () -> Unit
) {
    val context = LocalContext.current
    val weatherState by viewModel.weatherState.collectAsState()
    val cityName by viewModel.cityName.collectAsState()
    val pendingTasks by viewModel.pendingTasks.collectAsState()
    val aiGreeting by viewModel.aiGreeting.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    
    // Permission Handling
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                          permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                viewModel.fetchLocationAndWeather()
            }
        }
    )

    LaunchedEffect(Unit) {
        val fineLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        
        if (fineLoc || coarseLoc) {
            viewModel.fetchLocationAndWeather()
        } else {
            locationPermissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    Scaffold(
        containerColor = Color.Black, // Dark overall theme
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("V ", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        Text("é", color = AccentPrimary, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        Text(" r i t é", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AccentPrimary)
                    }
                },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Profile Icon
                        Image(
                            painter = painterResource(id = R.drawable.ic_account),
                            contentDescription = "Profile",
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E2929))
                                .border(1.dp, Color(0xFF1E2929), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Brain Icon
                        Icon(
                            painter = painterResource(id = R.drawable.ic_tmr_brain),
                            contentDescription = "TMR",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Sleep Band Image
            Image(
                painter = painterResource(id = R.drawable.headband),
                contentDescription = "Sleep Band",
                modifier = Modifier.size(200.dp, 140.dp),
                contentScale = ContentScale.Fit
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Device Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("V ", color = AccentPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("é", color = AccentPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(" r i t é ", color = AccentPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Sleep Band", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // AI Greeting
            if (aiGreeting != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1E2B2A).copy(alpha = 0.5f)
                ) {
                    Text(
                        text = aiGreeting ?: "",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        modifier = Modifier.padding(16.dp),
                        lineHeight = 22.sp
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            // Weather Card (Dark Green background)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF0A1A17) // Deep emerald green
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = cityName,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (weatherState != null) "Current Weather" else "Weather Unavailable",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth()) {
                        // Left Column
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = weatherState?.temperature?.toString() ?: "--",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(" C", color = AccentPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = weatherState?.windSpeed?.toString() ?: "--",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("km/h", color = AccentPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        // Right Column
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = weatherState?.humidity?.toString() ?: "--",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("%", color = AccentPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = weatherState?.uvIndex?.toString() ?: "--",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("UV -", color = AccentPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Health Brief Section (Tasks)
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Today's Health Brief", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (pendingTasks.isEmpty()) {
                    Text("No tasks for today. You are all caught up!", color = Color.Gray, modifier = Modifier.padding(16.dp))
                } else {
                    // Split tasks into two columns exactly as in the mock
                    val halfway = (pendingTasks.size + 1) / 2
                    val leftTasks = pendingTasks.take(halfway)
                    val rightTasks = pendingTasks.drop(halfway)
                    
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            leftTasks.forEach { task ->
                                BriefTaskItem(task.task)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            rightTasks.forEach { task ->
                                BriefTaskItem(task.task)
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Bottom Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onVoiceAssistant,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSpeaking) Color(0xFF8B0000) else Color(0xFF1A1A1A)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isSpeaking) "Stop Audio" else "Voice Assistant", color = Color.White, fontSize = 14.sp)
                }
                
                Button(
                    onClick = { viewModel.fetchLocationAndWeather() },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2B2A)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Refresh now !", color = Color.White, fontSize = 14.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun BriefTaskItem(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Circle,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 14.sp
        )
    }
}
