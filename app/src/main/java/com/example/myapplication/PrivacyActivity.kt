package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.components.SettingsSwitchRow
import com.example.myapplication.ui.components.TopBar
import com.example.myapplication.ui.home.SkyBackground
import com.example.myapplication.ui.theme.VeriteTheme
import com.example.myapplication.utils.SettingsManager

class PrivacyActivity : ComponentActivity() {
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)

        setContent {
            VeriteTheme {
                SkyBackground {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TopBar(
                            onBackClick = { finish() },
                            onProfileClick = { /* No-op on settings screens */ }
                        )

                        Spacer(modifier = Modifier.height(32.dp))
                        Text(
                            text = "Privacy & Security",
                            color = Color.White,
                            fontSize = 24.sp,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                        )

                        // Data Sharing
                        var shareAnalytics by remember { mutableStateOf(settingsManager.shareAnalytics) }
                        SettingsSwitchRow(
                            title = "Share Analytics",
                            description = "Help us improve by sharing anonymous usage data",
                            checked = shareAnalytics,
                            onCheckedChange = { 
                                shareAnalytics = it
                                settingsManager.shareAnalytics = it
                            }
                        )

                        // Biometrics
                        var biometricLogin by remember { mutableStateOf(settingsManager.biometricLogin) }
                        SettingsSwitchRow(
                            title = "Biometric Login",
                            description = "Use fingerprint or face unlock to access the app",
                            checked = biometricLogin,
                            onCheckedChange = {
                                biometricLogin = it
                                settingsManager.biometricLogin = it
                            }
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        // Privacy Policy Link (required by Google Play)
                        Text(
                            text = "Privacy Policy",
                            color = Color(0xFF1C9C91),
                            fontSize = 15.sp,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                                .clickable {
                                    startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://verite-app.com/privacy"))
                                    )
                                }
                        )

                        // Terms of Service Link
                        Text(
                            text = "Terms of Service",
                            color = Color(0xFF1C9C91),
                            fontSize = 15.sp,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                                .clickable {
                                    startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://verite-app.com/terms"))
                                    )
                                }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Vérité collects sleep, habit, and wellness data to provide personalised insights. " +
                                    "Your data is stored locally on your device. Location data is used only for weather in your morning brief. " +
                                    "Microphone access is used for voice commands and wake-word detection. " +
                                    "Bluetooth is used to connect to your sleep wearable.",
                            color = Color(0xFF7AA8A1),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
