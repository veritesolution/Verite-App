package com.mindsetpro.ui.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mindsetpro.ui.theme.MindSetColors
import kotlinx.coroutines.launch

/**
 * Onboarding / Welcome Screen — shown on first launch.
 *
 * 4 pages introducing core features:
 *   1. Welcome + overview
 *   2. Habits & Streaks
 *   3. Voice Commands & AI
 *   4. Analytics & Predictions
 */
data class OnboardingPage(
    val emoji: String,
    val title: String,
    val description: String,
    val features: List<String>
)

private val PAGES = listOf(
    OnboardingPage(
        emoji = "🧠",
        title = "Welcome to MindSet Pro",
        description = "Your AI-powered habit & task intelligence system",
        features = listOf(
            "📝 Manage tasks with smart priorities",
            "🎯 Track daily habits with streak rewards",
            "🤖 On-device ML behavioral analysis",
            "🔮 Predictive streak engine"
        )
    ),
    OnboardingPage(
        emoji = "🔥",
        title = "Habits & Streaks",
        description = "Build consistency with powerful streak tracking",
        features = listOf(
            "Set daily or weekly habit targets",
            "Watch your streak grow day by day",
            "90-day heatmap calendar visualization",
            "Compete with yourself on the leaderboard"
        )
    ),
    OnboardingPage(
        emoji = "🎤",
        title = "Voice Commands",
        description = "Hands-free task & habit management",
        features = listOf(
            "\"Add task called Review PR\"",
            "\"I completed meditation\"",
            "\"What's my streak for running?\"",
            "AI fallback for complex commands"
        )
    ),
    OnboardingPage(
        emoji = "📊",
        title = "Smart Analytics",
        description = "AI-powered insights into your productivity",
        features = listOf(
            "K-Means habit clustering by behavior",
            "Day-of-week productivity profiles",
            "Sentiment & momentum scoring",
            "Tomorrow's completion predictions"
        )
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { PAGES.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == PAGES.size - 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(MindSetColors.background, MindSetColors.surface)
                )
            )
    ) {
        // ── Skip Button ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onComplete) {
                Text("Skip", color = MindSetColors.textMuted, fontSize = 14.sp)
            }
        }

        // ── Pager ────────────────────────────────────────────────────────────
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            OnboardingPageContent(PAGES[page])
        }

        // ── Dots + Next/Get Started ──────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Dot indicators
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(PAGES.size) { index ->
                    val active = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(if (active) 24.dp else 8.dp, 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (active) MindSetColors.accentCyan
                                else MindSetColors.surface3
                            )
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // Next / Get Started button
            Button(
                onClick = {
                    if (isLastPage) {
                        onComplete()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLastPage) MindSetColors.accentGreen
                                     else MindSetColors.accentCyan,
                    contentColor = MindSetColors.background
                )
            ) {
                Text(
                    if (isLastPage) "Get Started 🚀" else "Next",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(page.emoji, fontSize = 64.sp)

        Spacer(Modifier.height(24.dp))

        Text(
            page.title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MindSetColors.text,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            page.description,
            fontSize = 14.sp,
            color = MindSetColors.textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        // Feature list
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MindSetColors.surface2,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                page.features.forEach { feature ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MindSetColors.accentCyan)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            feature,
                            fontSize = 14.sp,
                            color = MindSetColors.text,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }
}
