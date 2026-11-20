package com.brokenbored.neo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Small markdown renderer: fenced code blocks, headings, paragraphs, and
 * inline `code`. Enough for model replies; not a full markdown engine.
 * An unclosed fence (mid-stream) renders as code, so streaming never
 * shows raw backticks for long.
 */
private sealed interface Block {
    data class Para(val text: String) : Block
    data class Code(val language: String, val text: String) : Block
}

private fun parseMarkdown(text: String): List<Block> {
    val blocks = mutableListOf<Block>()
    var rest = text
    while (true) {
        val start = rest.indexOf("```")
        if (start == -1) {
            if (rest.isNotBlank()) blocks += Block.Para(rest.trim())
            break
        }
        val before = rest.substring(0, start)
        if (before.isNotBlank()) blocks += Block.Para(before.trim())
        val afterFence = rest.substring(start + 3)
        val end = afterFence.indexOf("```")
        if (end == -1) {
            val firstLineEnd = afterFence.indexOf('\n')
            val lang = if (firstLineEnd == -1) {
                afterFence.trim()
            } else {
                afterFence.substring(0, firstLineEnd).trim()
            }
            val code = if (firstLineEnd == -1) "" else afterFence.substring(firstLineEnd + 1)
            blocks += Block.Code(lang, code)
            break
        }
        val fenced = afterFence.substring(0, end)
        val firstLineEnd = fenced.indexOf('\n')
        val lang = if (firstLineEnd == -1) {
            fenced.trim()
        } else {
            fenced.substring(0, firstLineEnd).trim()
        }
        val code = if (firstLineEnd == -1) "" else fenced.substring(firstLineEnd + 1)
        blocks += Block.Code(lang, code.trimEnd('\n'))
        rest = afterFence.substring(end + 3)
    }
    return blocks
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parseMarkdown(text) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is Block.Code -> CodeBlock(block)
                is Block.Para -> block.text.split("\n").forEach { line ->
                    when {
                        line.startsWith("### ") -> Text(
                            line.removePrefix("### "),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        line.startsWith("## ") -> Text(
                            line.removePrefix("## "),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        line.startsWith("# ") -> Text(
                            line.removePrefix("# "),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        line.isBlank() -> Spacer(modifier = Modifier.height(4.dp))
                        else -> Text(
                            buildInline(line),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBlock(block: Block.Code) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(8.dp)
            )
            .padding(10.dp)
    ) {
        if (block.language.isNotBlank()) {
            Text(
                block.language,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(
            block.text,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.horizontalScroll(rememberScrollState())
        )
    }
}

/** Inline `code` spans inside one paragraph line. */
private fun buildInline(line: String) = buildAnnotatedString {
    val parts = line.split("`")
    parts.forEachIndexed { index, part ->
        if (index % 2 == 1) {
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                append(part)
            }
        } else {
            append(part)
        }
    }
}
