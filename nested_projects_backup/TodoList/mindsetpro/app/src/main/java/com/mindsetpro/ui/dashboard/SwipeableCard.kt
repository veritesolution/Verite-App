package com.mindsetpro.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mindsetpro.ui.theme.MindSetColors

/**
 * Swipe-to-dismiss wrapper for task/habit cards.
 *
 * Swipe right → Mark done / Toggle habit
 * Swipe left  → Delete
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableCard(
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    rightLabel: String = "Done",
    leftLabel: String = "Delete",
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onSwipeRight()
                    true
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onSwipeLeft()
                    true
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color by animateColorAsState(
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> MindSetColors.accentGreen.copy(alpha = 0.3f)
                    SwipeToDismissBoxValue.EndToStart -> MindSetColors.accentRed.copy(alpha = 0.3f)
                    else -> Color.Transparent
                },
                label = "swipe_color"
            )
            val icon = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Check
                SwipeToDismissBoxValue.EndToStart -> Icons.Default.Delete
                else -> Icons.Default.Check
            }
            val iconTint = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> MindSetColors.accentGreen
                SwipeToDismissBoxValue.EndToStart -> MindSetColors.accentRed
                else -> Color.Transparent
            }
            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                else -> Alignment.CenterEnd
            }
            val scale by animateFloatAsState(
                if (dismissState.targetValue == SwipeToDismissBoxValue.Settled) 0.75f else 1f,
                label = "icon_scale"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, shape = RoundedCornerShape(10.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.scale(scale),
                    tint = iconTint
                )
            }
        },
        content = { content() }
    )
}
