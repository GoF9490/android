package com.example.musicapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// 웹(tokens.css)과 동일한 다크 팔레트 (PRD §9.2)
val ColorBg = Color(0xFF030303)
val ColorBgElevated = Color(0xFF121212)
val ColorSurface = Color(0xFF212121)
val ColorAccent = Color(0xFFFF0000)
val ColorText = Color(0xFFFFFFFF)
val ColorTextMuted = Color(0xFFAAAAAA)
val ColorWarning = Color(0xFFFFB300)
val ColorSuccess = Color(0xFF4CAF50)
val ColorError = Color(0xFFFF5252)

private val MusicDarkColorScheme = darkColorScheme(
    primary = ColorAccent,
    background = ColorBg,
    surface = ColorBg,
    surfaceContainer = ColorBgElevated,
    surfaceVariant = ColorSurface,
    onPrimary = ColorText,
    onBackground = ColorText,
    onSurface = ColorText,
    onSurfaceVariant = ColorTextMuted,
    error = ColorError,
)

/** M3 스킴에 없는 색. `MaterialTheme.extendedColors`로 꺼내 쓴다. */
@Immutable
data class ExtendedColors(val warning: Color, val success: Color)

private val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(warning = ColorWarning, success = ColorSuccess)
}

val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendedColors.current

// 다크 테마 고정, 동적 색상(Material You)은 사용하지 않는다
// 주의: accent(primary, #FF0000)는 #212121 위 대비가 4.03:1이므로 텍스트 색으로 쓰지 않는다
@Composable
fun MusicAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MusicDarkColorScheme) {
        CompositionLocalProvider(
            LocalExtendedColors provides ExtendedColors(warning = ColorWarning, success = ColorSuccess),
            content = content,
        )
    }
}
