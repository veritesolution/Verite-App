package com.example.myapplication.ui.notification

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.model.AppNotification
import com.example.myapplication.data.model.NotificationType
import com.example.myapplication.ui.theme.*

// ════════════════════════════════════════════════════════════
// 1. NOTIFICATION BELL with BADGE (for top bar)
// ════════════════════════════════════════════════════════════

@Composable
fun NotificationBell(
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasUnread = unreadCount > 0

    // Subtle pulse animation for unread notifications
    val infiniteTransition = rememberInfiniteTransition(label = "bell_pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                if (hasUnread) AccentPrimary.copy(alpha = glowAlpha * 0.15f)
                else Color.Transparent
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (hasUnread) Icons.Filled.Notifications
                          else Icons.Outlined.Notifications,
            contentDescription = "Notifications",
            tint = if (hasUnread) AccentBright else Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(22.dp)
        )

        // Badge
        if (hasUnread) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-1).dp)
                    .size(if (unreadCount > 9) 18.dp else 16.dp)
                    .background(Color(0xFFF56C6C), CircleShape)
                    .border(1.5.dp, Color(0xFF050F0E), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                    color = Color.White,
                    fontSize = if (unreadCount > 9) 8.sp else 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
// 2. NOTIFICATION PANEL (slide-down overlay)
// ════════════════════════════════════════════════════════════

@Composable
fun NotificationPanel(
    notifications: List<AppNotification>,
    onMarkAllRead: () -> Unit,
    onClearAll: () -> Unit,
    onNotificationClick: (AppNotification) -> Unit,
    onDismiss: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.75f)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A1A18),
                        Color(0xFF071413)
                    )
                ),
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        AccentPrimary.copy(alpha = 0.3f),
                        AccentPrimary.copy(alpha = 0.05f)
                    )
                ),
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
            )
    ) {
        // ── Header ──────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Notifications",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (notifications.any { !it.isRead }) {
                    TextButton(onClick = onMarkAllRead) {
                        Text("Mark all read", color = AccentPrimary, fontSize = 12.sp)
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = AccentPrimary.copy(alpha = 0.12f), thickness = 0.5.dp)

        // ── Notification List ───────────────────────────────
        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.Notifications,
                        contentDescription = null,
                        tint = TextFaint,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "All caught up!",
                        color = TextMuted,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "No new notifications",
                        color = TextFaint,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(
                    items = notifications,
                    key = { it.id }
                ) { notification ->
                    NotificationItem(
                        notification = notification,
                        onClick = { onNotificationClick(notification) },
                        onDismiss = { onDismiss(notification.id) }
                    )
                }
            }
        }

        // ── Footer ──────────────────────────────────────────
        if (notifications.isNotEmpty()) {
            HorizontalDivider(color = AccentPrimary.copy(alpha = 0.12f), thickness = 0.5.dp)
            TextButton(
                onClick = onClearAll,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text("Clear all notifications", color = TextMuted, fontSize = 13.sp)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
// 3. SINGLE NOTIFICATION ITEM
// ════════════════════════════════════════════════════════════

@Composable
fun NotificationItem(
    notification: AppNotification,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val typeConfig = notificationTypeConfig(notification.type)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (notification.isRead) Color(0xFF0A1614)
                else Color(0xFF0D1E1B)
            )
            .border(
                width = 0.5.dp,
                color = if (notification.isRead) Color.Transparent
                        else typeConfig.color.copy(alpha = 0.2f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Icon circle
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(typeConfig.color.copy(alpha = 0.12f), CircleShape)
                .border(0.5.dp, typeConfig.color.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = typeConfig.icon,
                contentDescription = null,
                tint = typeConfig.color,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = notification.title,
                    color = if (notification.isRead) TextMuted else Color.White,
                    fontSize = 14.sp,
                    fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // Unread dot
                if (!notification.isRead) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(8.dp)
                            .background(typeConfig.color, CircleShape)
                    )
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = notification.message,
                color = if (notification.isRead) TextFaint else TextMuted,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatTimeAgo(notification.createdAt),
                color = TextUltraFaint,
                fontSize = 11.sp
            )
        }

        // Dismiss button
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Dismiss",
                tint = TextFaint,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// ════════════════════════════════════════════════════════════
// 4. IN-APP TOAST BANNER (slides in from top)
// ════════════════════════════════════════════════════════════

@Composable
fun VeriteToastBanner(
    notification: AppNotification?,
    onDismiss: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = notification != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        notification?.let { n ->
            val typeConfig = notificationTypeConfig(n.type)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                typeConfig.color.copy(alpha = 0.15f),
                                Color(0xFF0D1E1B)
                            )
                        )
                    )
                    .border(
                        0.5.dp,
                        typeConfig.color.copy(alpha = 0.3f),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable { onClick() }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = typeConfig.icon,
                    contentDescription = null,
                    tint = typeConfig.color,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = n.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = n.message,
                        color = TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = TextFaint,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
// HELPERS
// ════════════════════════════════════════════════════════════

private data class NotificationTypeConfig(
    val icon: ImageVector,
    val color: Color,
    val label: String
)

private fun notificationTypeConfig(type: NotificationType): NotificationTypeConfig = when (type) {
    NotificationType.INFO       -> NotificationTypeConfig(Icons.Default.Info, Color(0xFF1C9C91), "Info")
    NotificationType.SUCCESS    -> NotificationTypeConfig(Icons.Default.CheckCircle, Color(0xFF2DD4AA), "Success")
    NotificationType.WARNING    -> NotificationTypeConfig(Icons.Default.Warning, Color(0xFFE6A23C), "Warning")
    NotificationType.ERROR      -> NotificationTypeConfig(Icons.Default.Error, Color(0xFFF56C6C), "Error")
    NotificationType.AI_INSIGHT -> NotificationTypeConfig(Icons.Default.AutoAwesome, Color(0xFF23BFB3), "AI Insight")
    NotificationType.SLEEP      -> NotificationTypeConfig(Icons.Default.Bedtime, Color(0xFF3A8A9E), "Sleep")
    NotificationType.DEVICE     -> NotificationTypeConfig(Icons.Default.Bluetooth, Color(0xFF1C9C91), "Device")
    NotificationType.ACHIEVEMENT-> NotificationTypeConfig(Icons.Default.EmojiEvents, Color(0xFFD4C270), "Achievement")
    NotificationType.SYSTEM     -> NotificationTypeConfig(Icons.Default.Settings, Color(0xFFB4D2CD), "System")
}

private fun formatTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 60  -> "Just now"
        minutes < 60  -> "${minutes}m ago"
        hours < 24    -> "${hours}h ago"
        days < 7      -> "${days}d ago"
        else          -> "${days / 7}w ago"
    }
}
