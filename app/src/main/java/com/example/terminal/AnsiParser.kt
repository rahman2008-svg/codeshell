package com.example.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

object AnsiParser {

    private val ANSI_COLOR_MAP = mapOf(
        // Standard colors
        "30" to Color(0xFF1E1E1E), // Black
        "31" to Color(0xFFEF4444), // Red
        "32" to Color(0xFF22C55E), // Green
        "33" to Color(0xFFF59E0B), // Yellow
        "34" to Color(0xFF3B82F6), // Blue
        "35" to Color(0xFFA855F7), // Purple/Magenta
        "36" to Color(0xFF06B6D4), // Cyan
        "37" to Color(0xFFF1F5F9), // White

        // Bright colors
        "90" to Color(0xFF64748B), // Bright Black (Gray)
        "91" to Color(0xFFF87171), // Bright Red
        "92" to Color(0xFF4ADE80), // Bright Green
        "93" to Color(0xFFFBBF24), // Bright Yellow
        "94" to Color(0xFF60A5FA), // Bright Blue
        "95" to Color(0xFFC084FC), // Bright Purple
        "96" to Color(0xFF22D3EE), // Bright Cyan
        "97" to Color(0xFFFFFFFF), // Bright White

        // Extended styles
        "38;5;1" to Color(0xFFEF4444),
        "38;5;2" to Color(0xFF22C55E),
        "38;5;3" to Color(0xFFF59E0B),
        "38;5;4" to Color(0xFF3B82F6),
        "38;5;5" to Color(0xFFA855F7),
        "38;5;6" to Color(0xFF06B6D4)
    )

    fun parse(input: String, defaultColor: Color): AnnotatedString {
        return buildAnnotatedString {
            var i = 0
            val len = input.length
            
            // Current stateful style
            var currentColor = defaultColor
            var currentWeight = FontWeight.Normal

            while (i < len) {
                val escapeIdx = input.indexOf("\u001B[", i)
                if (escapeIdx == -1) {
                    // No more escape codes, append remainder
                    val remainder = input.substring(i)
                    pushStyle(SpanStyle(color = currentColor, fontWeight = currentWeight))
                    append(remainder)
                    pop()
                    break
                }

                // Append text before escape code
                if (escapeIdx > i) {
                    val preText = input.substring(i, escapeIdx)
                    pushStyle(SpanStyle(color = currentColor, fontWeight = currentWeight))
                    append(preText)
                    pop()
                }

                // Find the termination character 'm' for styling code
                val mIdx = input.indexOf('m', escapeIdx)
                if (mIdx == -1) {
                    // Invalid escape sequence, append the rest
                    pushStyle(SpanStyle(color = currentColor, fontWeight = currentWeight))
                    append(input.substring(escapeIdx))
                    pop()
                    break
                }

                // Extract style codes, e.g. "1;36" from "\u001B[1;36m"
                val codesStr = input.substring(escapeIdx + 2, mIdx)
                val codes = codesStr.split(";")

                for (code in codes) {
                    when {
                        code == "0" || code == "" -> {
                            currentColor = defaultColor
                            currentWeight = FontWeight.Normal
                        }
                        code == "1" -> {
                            currentWeight = FontWeight.Bold
                        }
                        ANSI_COLOR_MAP.containsKey(code) -> {
                            currentColor = ANSI_COLOR_MAP[code]!!
                        }
                    }
                }

                i = mIdx + 1
            }
        }
    }
}
