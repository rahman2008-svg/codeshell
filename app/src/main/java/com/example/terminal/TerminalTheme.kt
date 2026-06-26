package com.example.terminal

import androidx.compose.ui.graphics.Color

enum class TerminalTheme(
    val displayName: String,
    val background: Color,
    val foreground: Color,
    val accent: Color,
    val cursor: Color,
    val secondary: Color
) {
    HIGH_DENSITY(
        displayName = "High Density",
        background = Color(0xFF1C1B1F),
        foreground = Color(0xFFE6E1E5),
        accent = Color(0xFFD0BCFF),
        cursor = Color(0xFFD0BCFF),
        secondary = Color(0xFF938F99)
    ),
    CODESHELL(
        displayName = "CodeShell Slate",
        background = Color(0xFF0F172A), // slate-900
        foreground = Color(0xFFF1F5F9), // slate-100
        accent = Color(0xFF06B6D4),     // cyan-500
        cursor = Color(0xFF22D3EE),     // cyan-400
        secondary = Color(0xFF94A3B8)   // slate-400
    ),
    MATRIX(
        displayName = "Digital Matrix",
        background = Color(0xFF000000),
        foreground = Color(0xFF39FF14), // neon green
        accent = Color(0xFF00FF00),
        cursor = Color(0xFF39FF14),
        secondary = Color(0xFF008F11)   // dark green
    ),
    CRT_AMBER(
        displayName = "Retro CRT Amber",
        background = Color(0xFF0D0600),
        foreground = Color(0xFFFFB000), // amber
        accent = Color(0xFFFF8000),
        cursor = Color(0xFFFFB000),
        secondary = Color(0xFF805800)
    ),
    DRACULA(
        displayName = "Dracula Vampire",
        background = Color(0xFF282A36),
        foreground = Color(0xFFF8F8F2),
        accent = Color(0xFFFF79C6),     // pink
        cursor = Color(0xFF50FA7B),     // green
        secondary = Color(0xFFBD93F9)   // purple
    ),
    POWERSHELL(
        displayName = "PowerShell Blue",
        background = Color(0xFF012456),
        foreground = Color(0xFFFFFFFF),
        accent = Color(0xFFEECE00),     // gold
        cursor = Color(0xFFFFFFFF),
        secondary = Color(0xFF8C96AD)
    ),
    MONOCHROME(
        displayName = "Pure Monochrome",
        background = Color(0xFF121212),
        foreground = Color(0xFFFFFFFF),
        accent = Color(0xFFE0E0E0),
        cursor = Color(0xFFFFFFFF),
        secondary = Color(0xFF757575)
    )
}
