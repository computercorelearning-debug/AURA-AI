package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MarkdownRenderer(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.CodeBlock -> {
                    CodeSnippetCard(
                        code = block.code,
                        language = block.language
                    )
                }
                is MarkdownBlock.TextBlock -> {
                    val formatted = remember(block.text, textColor) {
                        parseInlineMarkdown(block.text, textColor)
                    }
                    Text(
                        text = formatted,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = 22.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun CodeSnippetCard(
    code: String,
    language: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .testTag("code_snippet_card"),
        color = Color(0xFF141226),
        contentColor = Color(0xFFE2E8F0)
    ) {
        Column {
            // Code block header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1A38))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "code" }.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA5B4FC)
                    )
                )

                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("code", code))
                        copied = true
                        Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                        scope.launch {
                            delay(2000)
                            copied = false
                        }
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("copy_code_button")
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = if (copied) Color(0xFF34D399) else Color(0xFFCBD5E1),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Code Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = Color(0xFFE2E8F0)
                    )
                )
            }
        }
    }
}

sealed class MarkdownBlock {
    data class TextBlock(val text: String) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
}

fun parseMarkdownBlocks(rawText: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val codeRegex = "(?s)```([a-zA-Z0-9_]*)\\n?(.*?)```".toRegex()
    var currentIndex = 0

    val matches = codeRegex.findAll(rawText).toList()
    for (match in matches) {
        if (match.range.first > currentIndex) {
            val textBefore = rawText.substring(currentIndex, match.range.first).trim()
            if (textBefore.isNotEmpty()) {
                blocks.add(MarkdownBlock.TextBlock(textBefore))
            }
        }
        val language = match.groupValues[1].trim()
        val code = match.groupValues[2].trimEnd()
        blocks.add(MarkdownBlock.CodeBlock(language, code))
        currentIndex = match.range.last + 1
    }

    if (currentIndex < rawText.length) {
        val remaining = rawText.substring(currentIndex).trim()
        if (remaining.isNotEmpty()) {
            blocks.add(MarkdownBlock.TextBlock(remaining))
        }
    }

    if (blocks.isEmpty() && rawText.isNotEmpty()) {
        blocks.add(MarkdownBlock.TextBlock(rawText))
    }

    return blocks
}

fun parseInlineMarkdown(text: String, defaultColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val regex = "(\\*\\*(.*?)\\*\\*)|(`(.*?)`)|(\\*([^*]+)\\*)".toRegex()

        val matches = regex.findAll(text).toList()
        for (match in matches) {
            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first))
            }

            val bold = match.groupValues[2]
            val inlineCode = match.groupValues[4]
            val italic = match.groupValues[6]

            when {
                bold.isNotEmpty() -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = defaultColor))
                    append(bold)
                    pop()
                }
                inlineCode.isNotEmpty() -> {
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0x22818CF8),
                            color = Color(0xFFA5B4FC)
                        )
                    )
                    append(" $inlineCode ")
                    pop()
                }
                italic.isNotEmpty() -> {
                    pushStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                    append(italic)
                    pop()
                }
            }
            cursor = match.range.last + 1
        }

        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}
