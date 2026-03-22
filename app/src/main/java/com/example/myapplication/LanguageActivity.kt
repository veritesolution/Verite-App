package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.components.TopBar
import com.example.myapplication.ui.home.SkyBackground
import com.example.myapplication.ui.theme.VeriteTheme
import com.example.myapplication.utils.SettingsManager

class LanguageActivity : ComponentActivity() {
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)

        val languages = listOf(
            "en" to "English",
            "es" to "Spanish",
            "fr" to "French"
        )

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
                            text = "Language",
                            color = Color.White,
                            fontSize = 24.sp,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                        )

                        var currentLang by remember { mutableStateOf(settingsManager.appLanguage) }

                        languages.forEach { (code, name) ->
                            LanguageOptionRow(
                                title = name,
                                selected = currentLang == code,
                                onClick = {
                                    currentLang = code
                                    settingsManager.appLanguage = code
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LanguageOptionRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, color = Color.White, fontSize = 18.sp)
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = androidx.compose.material3.RadioButtonDefaults.colors(
                selectedColor = Color(0xFF00FFB2),
                unselectedColor = Color.LightGray
            )
        )
    }
}
