package com.example.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

class SyntaxHighlightTransformation(private val extension: String, private val isDark: Boolean) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = HighlightUtils.highlightCode(text.text, extension, isDark)
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}
