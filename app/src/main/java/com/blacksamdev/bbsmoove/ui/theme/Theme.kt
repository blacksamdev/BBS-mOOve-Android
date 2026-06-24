package com.blacksamdev.bbsmoove.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val BbsMooveColorScheme = darkColorScheme(
    background = BgDeep,
    surface = BgPanel,
    primary = Gold,
    onBackground = Bone,
    onSurface = Bone,
)

@Composable
fun BbsMooveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BbsMooveColorScheme,
        content = content,
    )
}
