package com.example.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.graphics.Color

object HighlightUtils {
    private val darkKeywords = setOf(
        "const", "let", "var", "function", "return", "if", "else", "for", "while", "do",
        "switch", "case", "break", "continue", "default", "class", "extends", "new", "this",
        "import", "export", "from", "as", "try", "catch", "finally", "throw", "typeof", "instanceof"
    )

    private val darkBuiltins = setOf(
        "console", "log", "error", "warn", "document", "window", "fetch", "alert", "prompt",
        "confirm", "setTimeout", "setInterval", "clearTimeout", "clearInterval", "JSON", "parse", "stringify",
        "Math", "random", "floor", "ceil", "round", "min", "max", "abs"
    )

    fun highlightCode(text: String, extension: String, isDark: Boolean): AnnotatedString {
        val ext = extension.lowercase()
        return when {
            ext == "html" || ext == "xml" -> highlightHtml(text, isDark)
            ext == "css" -> highlightCss(text, isDark)
            ext == "js" || ext == "javascript" -> highlightJs(text, isDark)
            else -> AnnotatedString(text)
        }
    }

    private fun highlightHtml(text: String, isDark: Boolean): AnnotatedString = buildAnnotatedString {
        append(text)
        
        // Colors
        val tagColor = if (isDark) Color(0xFFF92672) else Color(0xFFC71585)
        val attrColor = if (isDark) Color(0xFFA6E22E) else Color(0xFF008000)
        val stringColor = if (isDark) Color(0xFFE6DB74) else Color(0xFFB51A1A)
        val commentColor = if (isDark) Color(0xFF75715E) else Color(0xFF808080)

        // Find comments first: <!-- ... -->
        val commentRegex = Regex("<!--[\\s\\S]*?-->")
        commentRegex.findAll(text).forEach { match ->
            addStyle(SpanStyle(color = commentColor), match.range.first, match.range.last + 1)
        }

        // Find tags: </?[a-zA-Z0-9:-]+>? and attributes
        val tagRegex = Regex("</?[a-zA-Z0-9:-]+(\\s+[^>]+)?>")
        tagRegex.findAll(text).forEach { match ->
            val tagText = match.value
            val matchStart = match.range.first
            
            // Highlight the tag name itself
            val nameRegex = Regex("^</?[a-zA-Z0-9:-]+")
            nameRegex.find(tagText)?.let { nameMatch ->
                addStyle(SpanStyle(color = tagColor), matchStart + nameMatch.range.first, matchStart + nameMatch.range.last + 1)
            }

            // Highlight attributes within the tag
            val attrRegex = Regex("(\\s+)([a-zA-Z0-9:-]+)(\\s*=\\s*\"[^\"]*\")")
            attrRegex.findAll(tagText).forEach { attrMatch ->
                val attrStart = matchStart + attrMatch.range.first
                val nameGroup = attrMatch.groups[2]
                val valGroup = attrMatch.groups[3]
                
                if (nameGroup != null) {
                    addStyle(SpanStyle(color = attrColor), matchStart + nameGroup.range.first, matchStart + nameGroup.range.last + 1)
                }
                if (valGroup != null) {
                    addStyle(SpanStyle(color = stringColor), matchStart + valGroup.range.first, matchStart + valGroup.range.last + 1)
                }
            }
            
            // Highlight brackets: <, >, </, />
            addStyle(SpanStyle(color = tagColor), matchStart, matchStart + if (tagText.startsWith("</")) 2 else 1)
            if (tagText.endsWith("/>")) {
                addStyle(SpanStyle(color = tagColor), matchStart + tagText.length - 2, matchStart + tagText.length)
            } else if (tagText.endsWith(">")) {
                addStyle(SpanStyle(color = tagColor), matchStart + tagText.length - 1, matchStart + tagText.length)
            }
        }
    }

    private fun highlightCss(text: String, isDark: Boolean): AnnotatedString = buildAnnotatedString {
        append(text)
        
        val selectorColor = if (isDark) Color(0xFF66D9EF) else Color(0xFF1E90FF)
        val propertyColor = if (isDark) Color(0xFFA6E22E) else Color(0xFF2E8B57)
        val valueColor = if (isDark) Color(0xFFFD971F) else Color(0xFFD2691E)
        val commentColor = if (isDark) Color(0xFF75715E) else Color(0xFF808080)

        // Find comments: /* ... */
        val commentRegex = Regex("/\\*[\\s\\S]*?\\*/")
        commentRegex.findAll(text).forEach { match ->
            addStyle(SpanStyle(color = commentColor), match.range.first, match.range.last + 1)
        }

        // Parse style blocks: selector { rules }
        val blockRegex = Regex("([^{]+)\\{([^}]+)\\}")
        blockRegex.findAll(text).forEach { match ->
            val selectorGroup = match.groups[1]
            if (selectorGroup != null) {
                addStyle(SpanStyle(color = selectorColor), selectorGroup.range.first, selectorGroup.range.last + 1)
            }
            
            val rulesGroup = match.groups[2]
            if (rulesGroup != null) {
                val rulesText = rulesGroup.value
                val rulesStart = rulesGroup.range.first
                
                // Highlight rules inside block: property: value;
                val ruleRegex = Regex("([a-zA-Z0-9-]+)\\s*:\\s*([^;]+);?")
                ruleRegex.findAll(rulesText).forEach { ruleMatch ->
                    val propGroup = ruleMatch.groups[1]
                    val valGroup = ruleMatch.groups[2]
                    
                    if (propGroup != null) {
                        addStyle(SpanStyle(color = propertyColor), rulesStart + propGroup.range.first, rulesStart + propGroup.range.last + 1)
                    }
                    if (valGroup != null) {
                        addStyle(SpanStyle(color = valueColor), rulesStart + valGroup.range.first, rulesStart + valGroup.range.last + 1)
                    }
                }
            }
        }
    }

    private fun highlightJs(text: String, isDark: Boolean): AnnotatedString = buildAnnotatedString {
        append(text)
        
        val keywordColor = if (isDark) Color(0xFFF92672) else Color(0xFFD01173)
        val builtinColor = if (isDark) Color(0xFF66D9EF) else Color(0xFF007ACC)
        val stringColor = if (isDark) Color(0xFFE6DB74) else Color(0xFF228B22)
        val commentColor = if (isDark) Color(0xFF75715E) else Color(0xFF808080)
        val numberColor = if (isDark) Color(0xFFAE81FF) else Color(0xFF8A2BE2)

        // Find comments: // ... or /* ... */
        val lineCommentRegex = Regex("//.*")
        lineCommentRegex.findAll(text).forEach { match ->
            addStyle(SpanStyle(color = commentColor), match.range.first, match.range.last + 1)
        }
        val blockCommentRegex = Regex("/\\*[\\s\\S]*?\\*/")
        blockCommentRegex.findAll(text).forEach { match ->
            addStyle(SpanStyle(color = commentColor), match.range.first, match.range.last + 1)
        }

        // Find strings: "..." or '...' or `...`
        val stringRegex = Regex("\"[^\"]*\"|'[^']*'|`[^`]*`")
        stringRegex.findAll(text).forEach { match ->
            addStyle(SpanStyle(color = stringColor), match.range.first, match.range.last + 1)
        }

        // Find numbers
        val numberRegex = Regex("\\b\\d+(\\.\\d+)?\\b")
        numberRegex.findAll(text).forEach { match ->
            addStyle(SpanStyle(color = numberColor), match.range.first, match.range.last + 1)
        }

        // Highlight keywords and builtins (exclude matching in comments or strings by doing precise word boundary searches)
        val wordRegex = Regex("\\b[a-zA-Z_][a-zA-Z0-9_]*\\b")
        wordRegex.findAll(text).forEach { match ->
            val word = match.value
            val start = match.range.first
            val end = match.range.last + 1

            if (darkKeywords.contains(word)) {
                addStyle(SpanStyle(color = keywordColor), start, end)
            } else if (darkBuiltins.contains(word)) {
                addStyle(SpanStyle(color = builtinColor), start, end)
            }
        }
    }
}
