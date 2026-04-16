package com.example.myapplication

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.model.AppNotification
import com.example.myapplication.ui.home.HomeScreen
import com.example.myapplication.ui.notification.NotificationPanel
import com.example.myapplication.ui.notification.NotificationViewModel
import com.example.myapplication.ui.notification.VeriteToastBanner
import com.example.myapplication.ui.theme.VeriteTheme
import kotlinx.coroutines.delay

class HeadbandHomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VeriteTheme {
                val notifViewModel: NotificationViewModel = viewModel()
                val notifications by notifViewModel.notifications.collectAsState()
                val unreadCount by notifViewModel.unreadCount.collectAsState()
                val isPanelOpen by notifViewModel.isPanelOpen.collectAsState()

                // Toast banner state
                var toastNotification by remember { mutableStateOf<AppNotification?>(null) }
                LaunchedEffect(notifications) {
                    val latest = notifications.firstOrNull()
                    if (latest != null && !latest.isRead) {
                        toastNotification = latest
                        delay(4000)
                        toastNotification = null
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    HomeScreen(
                        onBackClick = { finish() },
                        onProfileClick = {
                            startActivity(android.content.Intent(this@HeadbandHomeActivity, ProfileActivity::class.java))
                        },
                        onFeatureClick = { feature ->
                            val destinationClass = when (feature.id) {
                                0 -> TmrFeatureActivity::class.java
                                1 -> SleepDataActivity::class.java
                                2 -> BioFeedbackActivity::class.java
                                3 -> AdaptiveSoundActivity::class.java
                                4 -> AlarmActivity::class.java
                                5 -> MindSetActivity::class.java
                                7 -> MoodTrackingActivity::class.java
                                else -> null
                            }
                            destinationClass?.let {
                                startActivity(android.content.Intent(this@HeadbandHomeActivity, it))
                            }
                        },
                        notificationCount = unreadCount,
                        onNotificationClick = { notifViewModel.togglePanel() }
                    )

                    // ── Notification Panel Overlay ──
                    AnimatedVisibility(
                        visible = isPanelOpen,
                        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                        modifier = Modifier.align(Alignment.TopCenter)
                    ) {
                        NotificationPanel(
                            notifications = notifications,
                            onMarkAllRead = { notifViewModel.markAllAsRead() },
                            onClearAll = { notifViewModel.clearAll(); notifViewModel.closePanel() },
                            onNotificationClick = { notif ->
                                notifViewModel.markAsRead(notif.id)
                                notifViewModel.closePanel()
                            },
                            onDismiss = { id -> notifViewModel.deleteNotification(id) },
                            onClose = { notifViewModel.closePanel() }
                        )
                    }

                    // ── Toast Banner ──
                    VeriteToastBanner(
                        notification = toastNotification,
                        onDismiss = { toastNotification = null },
                        onClick = { toastNotification = null },
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }
        }
    }
}
