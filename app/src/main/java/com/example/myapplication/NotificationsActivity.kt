
package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.components.SettingsSwitchRow
import com.example.myapplication.ui.components.TopBar
import com.example.myapplication.ui.home.SkyBackground
import com.example.myapplication.ui.theme.VeriteTheme
import com.example.myapplication.utils.SettingsManager

/*NotificationsActivity class*/

class NotificationsActivity : ComponentActivity() {
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
                            text = "Notifications",
                            color = Color.White,
                            fontSize = 24.sp,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                        )

                        // All Notifications
                        var allNotifications by remember { mutableStateOf(settingsManager.allNotificationsEnabled) }
                        SettingsSwitchRow(
                            title = "Allow Notifications",
                            description = "Receive important updates and alerts",
                            checked = allNotifications,
                            onCheckedChange = { 
                                allNotifications = it
                                settingsManager.allNotificationsEnabled = it
                            }
                        )

                        // Daily Reminders
                        var dailyReminders by remember { mutableStateOf(settingsManager.dailyRemindersEnabled) }
                        SettingsSwitchRow(
                            title = "Daily Reminders",
                            description = "Get notified about your daily habits",
                            checked = dailyReminders,
                            onCheckedChange = { 
                                dailyReminders = it
                                settingsManager.dailyRemindersEnabled = it
                            }
                        )
                    }
                }
            }
        }
    }
}
