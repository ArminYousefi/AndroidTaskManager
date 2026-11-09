package com.google.mytaskmanager.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mytaskmanager.domain.rules.ColumnCategory
import com.google.mytaskmanager.domain.rules.TaskPriority
import com.google.mytaskmanager.domain.rules.TaskType

/** Semantic colours that Material's scheme has no slot for (status categories, success/warning, backdrop glow). */
@Immutable
data class ExtraColors(
    val success: Color, val warning: Color, val danger: Color,
    val todo: Color, val inProgress: Color, val done: Color,
    val column: Color, val glowA: Color, val glowB: Color, val glowC: Color
) {
    /** Accent colour of a column/status category. */
    fun category(c: ColumnCategory): Color = when (c) { ColumnCategory.TODO -> todo; ColumnCategory.IN_PROGRESS -> inProgress; ColumnCategory.DONE -> done }

    /** Colour of a priority glyph (red = urgent ... slate = whenever). */
    fun priority(p: TaskPriority): Color = when (p) {
        TaskPriority.HIGHEST -> danger; TaskPriority.HIGH -> Color(0xFFF97316); TaskPriority.MEDIUM -> warning
        TaskPriority.LOW -> inProgress; TaskPriority.LOWEST -> todo
    }

    /** Colour of an issue-type badge. */
    fun type(t: TaskType): Color = when (t) {
        TaskType.TASK -> inProgress; TaskType.STORY -> success; TaskType.BUG -> danger; TaskType.EPIC -> Color(0xFF8B5CF6)
    }
}

private val LightExtras = ExtraColors(Green700, Amber700, Red700, Slate600, Blue700, Green700, LightColumn, Indigo600, Teal500, Amber500)
private val DarkExtras = ExtraColors(Green400, Amber300, Red400, Slate400, Blue400, Green400, DarkColumn, Indigo300, Teal300, Amber300)
private val LocalExtraColors = staticCompositionLocalOf { LightExtras }

private val LightScheme = lightColorScheme(
    primary = Indigo600, onPrimary = Color.White, primaryContainer = Indigo50, onPrimaryContainer = Color(0xFF1B1A6B),
    secondary = Teal500, onSecondary = Color.White, tertiary = Amber500, onTertiary = Color(0xFF2B1B00),
    background = LightBackground, onBackground = LightOnSurface, surface = LightSurface, onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant, onSurfaceVariant = LightOnSurfaceVariant, outline = LightOutline,
    error = Red700, onError = Color.White
)
private val DarkScheme = darkColorScheme(
    primary = Indigo300, onPrimary = Color(0xFF14134A), primaryContainer = Color(0xFF2A2C6E), onPrimaryContainer = Color(0xFFE0E1FF),
    secondary = Teal300, onSecondary = Color(0xFF002B27), tertiary = Amber300, onTertiary = Color(0xFF2B1B00),
    background = DarkBackground, onBackground = DarkOnSurface, surface = DarkSurface, onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant, onSurfaceVariant = DarkOnSurfaceVariant, outline = DarkOutline,
    error = Red400, onError = Color(0xFF3B0A0A)
)

/** Generously rounded corners: soft shapes read as friendly and safe (and feel current). */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(30.dp)
)

/** Access to [ExtraColors] next to `MaterialTheme.colorScheme`. */
object AppTheme {
    val extra: ExtraColors @Composable @ReadOnlyComposable get() = LocalExtraColors.current
}

/**
 * Motion language. Springs (not fixed-duration tweens) are used for anything the user touches, because physical
 * motion feels responsive and alive; short tweens are used for colour/opacity. Always go through [spec] helpers so
 * "Remove animations" in system settings is honoured.
 */
object Motion {
    /** Snappy spring for pick-up / drop / press feedback. */
    fun <T> bouncy(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.62f, stiffness = 420f)

    /** Calm spring for layout changes. */
    fun <T> smooth(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 300f)

    /** Quick fade/colour change. */
    fun <T> quick(): FiniteAnimationSpec<T> = tween(durationMillis = 160)

    /** Standard enter/exit duration. */
    const val MEDIUM_MS = 300
}

/** True when the user disabled animations system-wide (Developer options / accessibility "Remove animations"). */
@Composable
fun rememberReduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/**
 * App theme.
 *
 * @param darkTheme explicit dark/light choice; callers resolve the user's Light/Dark/Automatic preference first.
 */
@Composable
fun MyTaskManagerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalExtraColors provides if (darkTheme) DarkExtras else LightExtras) {
        MaterialTheme(colorScheme = if (darkTheme) DarkScheme else LightScheme, typography = Typography, shapes = AppShapes, content = content)
    }
}
