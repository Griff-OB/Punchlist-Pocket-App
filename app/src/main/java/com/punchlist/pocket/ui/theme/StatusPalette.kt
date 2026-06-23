package com.punchlist.pocket.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Status accent colors (open / in-progress / resolved) that adapt to the
 * active theme. The raw [StatusOpen]/[StatusInProgress]/[StatusResolved]
 * values are tuned for light backgrounds; in dark mode their luminance is too
 * low, so callers should read them through [LocalStatusPalette] instead of the
 * top-level constants.
 */
data class StatusPalette(
    val open: Color,
    val inProgress: Color,
    val resolved: Color
)

/** Default (light-mode) status palette. */
val LightStatusPalette = StatusPalette(
    open = StatusOpen,
    inProgress = StatusInProgress,
    resolved = StatusResolved
)

/** Dark-mode status palette — brighter variants that read well on dark cards. */
val DarkStatusPalette = StatusPalette(
    open = StatusOpenDark,
    inProgress = StatusInProgressDark,
    resolved = StatusResolvedDark
)

val LocalStatusPalette = compositionLocalOf { LightStatusPalette }

/** Convenience accessor for the current theme's status accent palette. */
val statusPalette: StatusPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalStatusPalette.current
