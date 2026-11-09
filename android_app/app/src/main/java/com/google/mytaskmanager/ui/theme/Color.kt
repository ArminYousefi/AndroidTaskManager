package com.google.mytaskmanager.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * COLOUR PSYCHOLOGY - every colour has a job
 * -------------------------------------------
 * Indigo   (primary)   trust + focus; the colour of the main action, calm enough for hours of use.
 * Teal     (secondary) growth + calm progress; used for "in motion" accents and the aurora backdrop.
 * Amber    (tertiary)  warmth + attention; due-soon, "in review", WIP-limit warnings.
 * Green    (success)   reward; reserved for DONE so finishing something *feels* different from everything else.
 * Red      (danger)    urgency; only overdue / destructive actions - rare, so it always gets noticed.
 * Slate    (todo)      neutral "not started": it must not compete for attention with active work.
 * Dark theme uses a deep blue-black (not pure black) with lifted, slightly desaturated accents: lower glare for
 * evening use and AA contrast on every text/background pair.
 */

// ---- light
val Indigo600 = Color(0xFF4F46E5)
val Indigo50 = Color(0xFFEEF0FF)
val Teal500 = Color(0xFF14B8A6)
val Amber500 = Color(0xFFF59E0B)
val Green500 = Color(0xFF16A34A)
val Red500 = Color(0xFFDC2626)
val Slate500 = Color(0xFF64748B)
val Blue500 = Color(0xFF2563EB)
val LightBackground = Color(0xFFF5F6FB)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEBEDF7)
val LightColumn = Color(0xFFEBEDF6)
val LightOnSurface = Color(0xFF11142B)
val LightOnSurfaceVariant = Color(0xFF53587A)
val LightOutline = Color(0xFFC9CDE3)

// ---- light, text-safe variants: the 500-level hues above fail WCAG AA as *text* on white (amber 2.2:1, green 3.3:1),
// so everything that is drawn as text or sits under white text uses the 600/700 shades.
val Green700 = Color(0xFF15803D)
val Amber700 = Color(0xFFB45309)
val Red700 = Color(0xFFB91C1C)
val Slate600 = Color(0xFF475569)
val Blue700 = Color(0xFF1D4ED8)

// ---- dark
val Indigo300 = Color(0xFF9DA0FF)
val Teal300 = Color(0xFF4FD1C5)
val Amber300 = Color(0xFFFBBF24)
val Green400 = Color(0xFF4ADE80)
val Red400 = Color(0xFFF87171)
val Slate400 = Color(0xFF94A3B8)
val Blue400 = Color(0xFF60A5FA)
val DarkBackground = Color(0xFF0A0C18)
val DarkSurface = Color(0xFF141830)
val DarkSurfaceVariant = Color(0xFF1D2242)
val DarkColumn = Color(0xFF10132A)
val DarkOnSurface = Color(0xFFEDEFFD)
val DarkOnSurfaceVariant = Color(0xFFA6ABD0)
val DarkOutline = Color(0xFF3A3F66)
