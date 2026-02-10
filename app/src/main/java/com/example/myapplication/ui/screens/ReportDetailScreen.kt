package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.components.VeriteTopBar
import com.example.myapplication.ui.theme.*
import com.example.myapplication.utils.ReportUtils
import java.io.File

@Composable
fun ReportDetailScreen(
    reportFileName: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    val reportContent = remember {
        val directory = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
        val file = File(directory, reportFileName)
        if (file.exists()) {
            ReportUtils.readReportContent(file)
        } else {
            "Report not found."
        }
    }

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
                .padding(16.dp)
        ) {
            Text(
                text = "Report Detail",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                color = CardBackground.copy(alpha = 0.3f),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TealPrimary.copy(alpha = 0.3f))
            ) {
                Box(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        text = reportContent,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 20.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onBackClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CardBackground),
                shape = RoundedCornerShape(30.dp)
            ) {
                Text(
                    text = "Close",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
