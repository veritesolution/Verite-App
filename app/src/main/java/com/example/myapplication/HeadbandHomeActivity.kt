package com.example.myapplication

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.ui.home.HomeScreen
import com.example.myapplication.ui.theme.VeriteTheme

class HeadbandHomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VeriteTheme {
                HomeScreen(
                    onBackClick = { finish() },
                    onProfileClick = {
                        startActivity(android.content.Intent(this, ProfileActivity::class.java))
                    },
                    onFeatureClick = { feature ->
                        val destinationClass = when (feature.id) {
                            0 -> TmrFeatureActivity::class.java
                            1 -> SleepDataActivity::class.java
                            2 -> BioFeedbackActivity::class.java
                            3 -> AdaptiveSoundActivity::class.java
                            4 -> AlarmActivity::class.java
                            5 -> MindSetActivity::class.java
                            6 -> BioWearableDiagnosticActivity::class.java
                            else -> null
                        }
                        destinationClass?.let {
                            startActivity(android.content.Intent(this, it))
                        }
                    }
                )
            }
        }
    }
}
