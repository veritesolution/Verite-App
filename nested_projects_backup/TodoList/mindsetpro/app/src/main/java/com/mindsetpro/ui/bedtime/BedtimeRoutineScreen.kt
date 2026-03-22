package com.mindsetpro.ui.bedtime

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mindsetpro.data.model.BedtimeItem
import com.mindsetpro.ui.theme.MindSetColors

/**
 * 🌙 Bedtime Routine Screen
 *
 * A guided nightly wind-down checklist with progress tracking.
 * Mirrors the BedtimeRoutine class from the notebook.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BedtimeRoutineScreen(
    items: List<BedtimeItem>,
    completionPercent: Float,
    onToggleItem: (String, Boolean) -> Unit,
    onReset: () -> Unit
) {
    val allDone = items.isNotEmpty() && items.all { it.isChecked }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(MindSetColors.background, MindSetColors.surface)
                )
            )
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))

        // ── Header ───────────────────────────────────────────────────────────
        Text(
            "🌙 Bedtime Routine",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MindSetColors.text
        )
        Text(
            "Wind down and prepare for a restful night",
            fontSize = 13.sp,
            color = MindSetColors.textMuted
        )

        Spacer(Modifier.height(20.dp))

        // ── Progress Bar ─────────────────────────────────────────────────────
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MindSetColors.surface2,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${(completionPercent * 100).toInt()}% Complete",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (allDone) MindSetColors.accentGreen else MindSetColors.accentPurple
                    )
                    if (allDone) {
                        Text("✨ All done!", fontSize = 13.sp, color = MindSetColors.accentGreen)
                    }
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { completionPercent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = if (allDone) MindSetColors.accentGreen else MindSetColors.accentPurple,
                    trackColor = MindSetColors.surface3,
                    strokeCap = StrokeCap.Round
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Checklist ────────────────────────────────────────────────────────
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                BedtimeItemCard(
                    item = item,
                    stepNumber = index + 1,
                    isActive = !item.isChecked && (index == 0 || items[index - 1].isChecked),
                    onToggle = { onToggleItem(item.id, !item.isChecked) }
                )
            }
        }

        // ── Reset Button ─────────────────────────────────────────────────────
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MindSetColors.textMuted
            )
        ) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Reset Routine", fontSize = 13.sp)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun BedtimeItemCard(
    item: BedtimeItem,
    stepNumber: Int,
    isActive: Boolean,
    onToggle: () -> Unit
) {
    val cardAlpha = when {
        item.isChecked -> 0.5f
        isActive -> 1f
        else -> 0.7f
    }

    val borderColor = when {
        item.isChecked -> MindSetColors.accentGreen.copy(alpha = 0.3f)
        isActive -> MindSetColors.accentPurple.copy(alpha = 0.6f)
        else -> MindSetColors.surface3
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(cardAlpha),
        shape = RoundedCornerShape(12.dp),
        color = MindSetColors.surface2,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        onClick = onToggle
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Step number / check indicator
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (item.isChecked)
                    MindSetColors.accentGreen.copy(alpha = 0.2f)
                else
                    MindSetColors.surface3,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    if (item.isChecked) {
                        Icon(
                            Icons.Default.Check,
                            null,
                            tint = MindSetColors.accentGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text(
                            "$stepNumber",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) MindSetColors.accentPurple else MindSetColors.textMuted
                        )
                    }
                }
            }

            Spacer(Modifier.width(14.dp))

            Text(
                item.name,
                fontSize = 15.sp,
                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                color = if (item.isChecked) MindSetColors.textMuted else MindSetColors.text,
                textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None
            )
        }
    }
}
