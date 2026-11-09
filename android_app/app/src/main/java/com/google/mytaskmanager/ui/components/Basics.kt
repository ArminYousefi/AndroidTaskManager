package com.google.mytaskmanager.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mytaskmanager.domain.rules.BoardRole
import com.google.mytaskmanager.domain.rules.TaskPriority
import com.google.mytaskmanager.domain.rules.TaskType
import com.google.mytaskmanager.ui.theme.AppTheme
import com.google.mytaskmanager.ui.theme.Motion
import com.google.mytaskmanager.ui.theme.rememberReduceMotion
import kotlin.math.abs

private val AvatarPalette = listOf(
    Color(0xFF6366F1), Color(0xFF0EA5E9), Color(0xFF14B8A6), Color(0xFF22C55E),
    Color(0xFFF59E0B), Color(0xFFF97316), Color(0xFFEC4899), Color(0xFF8B5CF6)
)

/** Round avatar with the person's initials on a colour derived from their name (stable, so a person always looks the same). */
@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val initials = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "?" }
    Box(
        modifier.size(size).clip(CircleShape).background(AvatarPalette[abs(name.hashCode()) % AvatarPalette.size])
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center
    ) { Text(initials, color = Color.White, fontSize = (size.value * 0.4f).sp, fontWeight = FontWeight.SemiBold) }
}

/**
 * Slowly drifting colour glows behind a screen: a calm, living backdrop for the first impression (auth/onboarding).
 * Static when the user disabled animations.
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val reduce = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "aurora")
    val drift by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(16_000, easing = LinearEasing), RepeatMode.Reverse), label = "drift")
    val t = if (reduce) 0.5f else drift
    val extra = AppTheme.extra
    Box(modifier.background(MaterialTheme.colorScheme.background)) {
        Canvas(Modifier.fillMaxSize()) {
            fun glow(color: Color, cx: Float, cy: Float, r: Float) =
                drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.30f), Color.Transparent), center = Offset(cx, cy), radius = r), radius = r, center = Offset(cx, cy))
            glow(extra.glowA, size.width * (0.15f + 0.45f * t), size.height * 0.12f, size.width * 0.9f)
            glow(extra.glowB, size.width * (0.95f - 0.5f * t), size.height * (0.45f + 0.1f * t), size.width * 0.8f)
            glow(extra.glowC, size.width * (0.2f + 0.3f * t), size.height * 0.95f, size.width * 0.7f)
        }
        content()
    }
}

/** The primary call-to-action: an indigo->teal gradient that visibly "presses in". One per screen (Von Restorff: it stands out). */
@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, Motion.bouncy(), label = "press")
    val scheme = MaterialTheme.colorScheme
    val fill: Brush = if (enabled) Brush.horizontalGradient(listOf(scheme.primary, AppTheme.extra.glowB)) else SolidColor(scheme.surfaceVariant)
    Box(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }.heightIn(min = 54.dp).clip(RoundedCornerShape(18.dp)).background(fill)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = scheme.onPrimary, strokeWidth = 2.5.dp)
        else Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) scheme.onPrimary else scheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** Standard text field: rounded, with inline error/help text. */
@Composable
fun AppTextField(
    value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    error: String? = null, help: String? = null, singleLine: Boolean = true, enabled: Boolean = true, readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default, trailing: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None, minLines: Int = 1
) {
    val supporting: (@Composable () -> Unit)? = (error ?: help)?.let { msg -> @Composable { Text(msg) } }
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) }, modifier = modifier.fillMaxWidth(),
        isError = error != null, enabled = enabled, readOnly = readOnly, singleLine = singleLine, minLines = minLines,
        keyboardOptions = keyboardOptions, trailingIcon = trailing, visualTransformation = visualTransformation,
        shape = MaterialTheme.shapes.medium,
        supportingText = supporting
    )
}

/** Password input with a Show/Hide toggle. */
@Composable
fun PasswordField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, error: String? = null, help: String? = null, keyboardOptions: KeyboardOptions = KeyboardOptions.Default) {
    var visible by rememberSaveable { mutableStateOf(false) }
    AppTextField(
        value, onValueChange, label, modifier, error, help, keyboardOptions = keyboardOptions,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailing = { TextButton(onClick = { visible = !visible }) { Text(if (visible) "Hide" else "Show") } }
    )
}

/** Small rounded pill with a tinted background. */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    // Text is the accent colour blended 30% towards the normal text colour: keeps the hue recognisable while the small
    // 11sp label stays above the 4.5:1 WCAG AA contrast on its 10% tint (verified for every accent in both themes).
    val textColor = androidx.compose.ui.graphics.lerp(color, MaterialTheme.colorScheme.onSurface, 0.3f)
    Row(
        modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.10f)).padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(12.dp), tint = textColor)
        Text(text, color = textColor, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/** Role pill (Viewer / Member / Admin) with a distinct colour per power level. */
@Composable
fun RoleChip(role: String, modifier: Modifier = Modifier) {
    val parsed = BoardRole.parseOrNull(role)
    val color = when (parsed) {
        BoardRole.ADMIN -> MaterialTheme.colorScheme.primary
        BoardRole.MEMBER -> AppTheme.extra.success
        else -> AppTheme.extra.todo
    }
    Tag(role.lowercase().replaceFirstChar { it.uppercase() }, color, modifier)
}

/** Issue-type badge: coloured rounded square with the type's initial (T/S/B/E). */
@Composable
fun TypeBadge(type: TaskType, modifier: Modifier = Modifier) {
    Box(
        modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).background(AppTheme.extra.type(type)).semantics { contentDescription = type.name.lowercase() },
        contentAlignment = Alignment.Center
    ) { Text(type.name.take(1), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
}

/** Priority glyph in Jira's spirit: ▲▲ highest, ▲ high, = medium, ▼ low, ▼▼ lowest. */
@Composable
fun PriorityGlyph(priority: TaskPriority, modifier: Modifier = Modifier) {
    val glyph = when (priority) {
        TaskPriority.HIGHEST -> "▲▲"; TaskPriority.HIGH -> "▲"; TaskPriority.MEDIUM -> "="; TaskPriority.LOW -> "▼"; TaskPriority.LOWEST -> "▼▼"
    }
    Text(glyph, modifier.semantics { contentDescription = "${priority.name.lowercase()} priority" }, color = AppTheme.extra.priority(priority), fontSize = 11.sp, fontWeight = FontWeight.Bold)
}

/** Circular progress with the percentage in the middle (Zeigarnik effect: visible progress motivates finishing). */
@Composable
fun ProgressRing(progress: Float, modifier: Modifier = Modifier, size: Dp = 44.dp, stroke: Dp = 5.dp, color: Color = AppTheme.extra.success) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), Motion.smooth(), label = "ring")
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val d = Size(this.size.width - s, this.size.height - s)
            drawArc(track, 0f, 360f, false, Offset(s / 2, s / 2), d, style = Stroke(s))
            drawArc(color, -90f, 360f * animated, false, Offset(s / 2, s / 2), d, style = Stroke(s, cap = StrokeCap.Round))
        }
        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

/** Placeholder block with a moving highlight, shown while data loads (perceived speed beats spinners). */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(14.dp)) {
    val reduce = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(-1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "x")
    val base = MaterialTheme.colorScheme.surfaceVariant
    val hi = MaterialTheme.colorScheme.surface
    val pos = if (reduce) 0.5f else x
    Box(modifier.clip(shape).background(Brush.horizontalGradient(listOf(base, hi, base), startX = pos * 600f - 300f, endX = pos * 600f + 300f)))
}

/** Friendly empty screen: say what is missing and what to do about it. */
@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) GradientButton(actionLabel, onAction, Modifier.padding(top = 8.dp))
    }
}

/** Small uppercase section label. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier.padding(horizontal = 4.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}

/** Card-like surface used by settings sections. */
@Composable
fun SoftCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), MaterialTheme.shapes.large),
        content = content
    )
}
