package com.google.mytaskmanager.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.google.mytaskmanager.domain.rules.PasswordStrength
import com.google.mytaskmanager.domain.rules.RuleResult
import com.google.mytaskmanager.ui.icons.AppIcons
import com.google.mytaskmanager.ui.theme.AppTheme
import com.google.mytaskmanager.ui.theme.Motion

/**
 * The live "password requirements" box. Every rule shows a green tick or a red cross that updates *as the user types*
 * (the rules come from the same `PasswordPolicy` the server enforces). Ticks pop with a tiny spring so each
 * completed requirement gives a small reward; a four-segment meter summarises strength.
 *
 * @param password current input (drives the strength meter and the neutral state before typing).
 * @param results output of `PasswordPolicy.evaluate`.
 * @param extra additional UI-only rules such as "Passwords match".
 */
@Composable
fun PasswordChecklist(password: String, results: List<RuleResult>, strength: PasswordStrength, modifier: Modifier = Modifier, extra: List<Pair<String, Boolean>> = emptyList()) {
    val started = password.isNotEmpty()
    Column(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Password requirements", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(strengthLabel(strength), style = MaterialTheme.typography.labelMedium, color = strengthColor(strength))
        }
        StrengthMeter(strength)
        results.forEach { RuleRow(it.rule.label, it.satisfied, started) }
        extra.forEach { (label, ok) -> RuleRow(label, ok, started) }
    }
}

@Composable
private fun StrengthMeter(strength: PasswordStrength) {
    val filled = when (strength) { PasswordStrength.NONE -> 0; PasswordStrength.WEAK -> 1; PasswordStrength.FAIR -> 2; PasswordStrength.GOOD -> 3; PasswordStrength.STRONG -> 4 }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(4) { i ->
            val c by animateColorAsState(if (i < filled) strengthColor(strength) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), Motion.quick(), label = "seg$i")
            Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c))
        }
    }
}

@Composable
private fun RuleRow(label: String, ok: Boolean, started: Boolean) {
    val textColor by animateColorAsState(if (ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, Motion.quick(), label = "txt")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RuleIcon(ok, started)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = textColor)
    }
}

@Composable
private fun RuleIcon(ok: Boolean, started: Boolean) {
    val pop = remember { Animatable(1f) }
    LaunchedEffect(ok) { if (ok) { pop.animateTo(1.3f, tween(90)); pop.animateTo(1f, Motion.bouncy()) } }
    val bg by animateColorAsState(
        when { ok -> AppTheme.extra.success; started -> AppTheme.extra.danger; else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f) },
        Motion.quick(), label = "bg"
    )
    Box(Modifier.size(20.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }.clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Crossfade(targetState = ok, animationSpec = Motion.quick(), label = "icon") { good ->
            Icon(if (good) AppIcons.Check else AppIcons.Close, if (good) "met" else "not met", Modifier.size(13.dp), tint = Color.White)
        }
    }
}

@Composable
private fun strengthColor(s: PasswordStrength): Color = when (s) {
    PasswordStrength.NONE -> MaterialTheme.colorScheme.outline
    PasswordStrength.WEAK -> AppTheme.extra.danger
    PasswordStrength.FAIR -> AppTheme.extra.warning
    PasswordStrength.GOOD -> AppTheme.extra.inProgress
    PasswordStrength.STRONG -> AppTheme.extra.success
}

private fun strengthLabel(s: PasswordStrength) = when (s) {
    PasswordStrength.NONE -> ""; PasswordStrength.WEAK -> "Weak"; PasswordStrength.FAIR -> "Fair"; PasswordStrength.GOOD -> "Good"; PasswordStrength.STRONG -> "Strong"
}
