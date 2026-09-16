package com.adamway.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AdamWayColorScheme = darkColorScheme(
    primary = AdamWayWhite,
    onPrimary = AdamWayBlack,
    secondary = AdamWayAccent,
    onSecondary = AdamWayBlack,
    background = AdamWayBlack,
    onBackground = AdamWayWhite,
    surface = AdamWayGrey,
    onSurface = AdamWayWhite,
    error = AdamWayError,
    onError = AdamWayWhite,
)

/** Adam Way is deliberately always-black / always-white — no light theme, no dynamic color. */
@Composable
fun AdamWayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AdamWayColorScheme,
        typography = AdamWayTypography,
        content = content,
    )
}
