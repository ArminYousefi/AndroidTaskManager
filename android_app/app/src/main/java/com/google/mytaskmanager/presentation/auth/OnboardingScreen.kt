package com.google.mytaskmanager.presentation.auth

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.mytaskmanager.ui.components.Avatar
import com.google.mytaskmanager.ui.components.AuroraBackground
import com.google.mytaskmanager.ui.components.GradientButton
import com.google.mytaskmanager.ui.theme.AppTheme
import com.google.mytaskmanager.ui.theme.Motion
import kotlinx.coroutines.launch

private data class Slide(val title: String, val body: String)

private val Slides = listOf(
    Slide("Plan it your way", "Boards, columns and issues, just like Jira: set priorities, due dates and WIP limits."),
    Slide("Hold. Drag. Done.", "Press and hold a card to move it between columns, or hold still for rename, replan and delete."),
    Slide("Better together", "Invite people and whole teams to a board with Viewer, Member or Admin access - and see everything that happens in the activity log.")
)

/** Three animated intro slides (skippable). Finishing marks onboarding as seen. */
@Composable
fun OnboardingScreen(onFinished: () -> Unit, vm: AuthViewModel = hiltViewModel()) {
    val pager = rememberPagerState(pageCount = { Slides.size })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == Slides.lastIndex
    fun finish() { vm.markOnboardingSeen(); onFinished() }

    AuroraBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { finish() }) { Text("Skip") }
            }
            HorizontalPager(pager, Modifier.weight(1f)) { page ->
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.height(240.dp), contentAlignment = Alignment.Center) {
                        when (page) { 0 -> HeroPlan(); 1 -> HeroMove(); else -> HeroTeam() }
                    }
                    Spacer(Modifier.height(28.dp))
                    Text(Slides[page].title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Text(Slides[page].body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.Center) {
                repeat(Slides.size) { i ->
                    val w by animateDpAsState(if (i == pager.currentPage) 26.dp else 8.dp, Motion.bouncy(), label = "dot")
                    Box(Modifier.padding(horizontal = 4.dp).height(8.dp).width(w).clip(CircleShape).background(if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline))
                }
            }
            GradientButton(if (last) "Get started" else "Next", { if (last) finish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun HeroPlan() {
    val t = rememberInfiniteTransition(label = "plan")
    val dy by t.animateFloat(-8f, 8f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dy")
    val colors = listOf(AppTheme.extra.todo, AppTheme.extra.inProgress, AppTheme.extra.done)
    Box(Modifier.size(230.dp, 200.dp)) {
        colors.forEachIndexed { i, c ->
            Box(
                Modifier.align(Alignment.Center).offset(x = (i * 16 - 16).dp, y = ((1 - i) * 54 + dy * (i + 1) / 3f).dp)
                    .size(170.dp, 62.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)
            ) { Box(Modifier.padding(14.dp).size(46.dp, 8.dp).clip(CircleShape).background(c)) }
        }
    }
}

@Composable
private fun HeroMove() {
    val t = rememberInfiniteTransition(label = "move")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    Box(Modifier.size(260.dp, 190.dp)) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(2) { Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(AppTheme.extra.column)) }
        }
        Box(
            Modifier.offset(x = (14 + 118 * p).dp, y = (54 - 26 * p).dp).size(100.dp, 64.dp).clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.92f))
        ) { Box(Modifier.padding(14.dp).size(44.dp, 8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))) }
    }
}

@Composable
private fun HeroTeam() {
    val t = rememberInfiniteTransition(label = "team")
    val liftPx by t.animateFloat(0f, -10f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "lift")
    val lift = liftPx.dp
    Row(horizontalArrangement = Arrangement.spacedBy((-18).dp)) {
        listOf("Ava Stone", "Sam Lee", "Nora K", "Omid R").forEachIndexed { i, n ->
            Avatar(n, Modifier.offset(y = if (i % 2 == 0) lift else -lift), size = 76.dp)
        }
    }
}
