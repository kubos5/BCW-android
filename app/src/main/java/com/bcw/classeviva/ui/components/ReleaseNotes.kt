package com.bcw.classeviva.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.bcw.classeviva.ui.theme.Theme

/** Un blocco delle note di rilascio. */
sealed interface ReleaseBlock {
    data class Heading(val level: Int, val text: String) : ReleaseBlock
    data class Paragraph(val text: String) : ReleaseBlock
    data class Bullet(val text: String) : ReleaseBlock
    data class Numbered(val number: String, val text: String) : ReleaseBlock
    data class Quote(val text: String) : ReleaseBlock
}

/** Un pezzo di testo in riga con il suo stile. */
data class InlineSpan(val text: String, val bold: Boolean = false, val italic: Boolean = false, val code: Boolean = false)

private val numbered = Regex("^(\\d+)\\.\\s+(.*)$")
private val inline = Regex("\\*\\*(.+?)\\*\\*|`([^`]+)`|\\[([^\\]]+)]\\([^)]*\\)|\\*(?!\\s)(.+?)\\*|_(.+?)_")

/** Le note come blocchi: titoli, elenchi, citazioni e paragrafi (il Markdown delle release, senza immagini). */
fun releaseBlocks(markdown: String): List<ReleaseBlock> {
    val blocks = mutableListOf<ReleaseBlock>()
    val paragraph = mutableListOf<String>()
    fun flush() {
        if (paragraph.isNotEmpty()) blocks += ReleaseBlock.Paragraph(paragraph.joinToString(" "))
        paragraph.clear()
    }
    for (raw in markdown.replace("\r", "").lines()) {
        val line = raw.trim()
        when {
            line.isEmpty() -> flush()
            line.matches(Regex("^-{3,}$")) -> flush()
            line.startsWith("#") -> {
                flush()
                val level = line.takeWhile { it == '#' }.length
                blocks += ReleaseBlock.Heading(level.coerceIn(1, 3), line.drop(level).trim())
            }
            line.startsWith("- ") || line.startsWith("* ") -> {
                flush()
                blocks += ReleaseBlock.Bullet(line.drop(2).trim())
            }
            numbered.matches(line) -> {
                flush()
                val match = numbered.find(line)!!
                blocks += ReleaseBlock.Numbered(match.groupValues[1], match.groupValues[2])
            }
            line.startsWith(">") -> {
                flush()
                blocks += ReleaseBlock.Quote(line.drop(1).trim())
            }
            else -> paragraph += line
        }
    }
    flush()
    return blocks
}

/** Il testo di una riga con il grassetto, il corsivo, il codice e i collegamenti (solo il testo). */
fun inlineSpans(text: String): List<InlineSpan> {
    val spans = mutableListOf<InlineSpan>()
    var last = 0
    for (match in inline.findAll(text)) {
        if (match.range.first > last) spans += InlineSpan(text.substring(last, match.range.first))
        val group = match.groups
        when {
            group[1] != null -> spans += InlineSpan(group[1]!!.value, bold = true)
            group[2] != null -> spans += InlineSpan(group[2]!!.value, code = true)
            group[3] != null -> spans += InlineSpan(group[3]!!.value)
            group[4] != null -> spans += InlineSpan(group[4]!!.value, italic = true)
            group[5] != null -> spans += InlineSpan(group[5]!!.value, italic = true)
        }
        last = match.range.last + 1
    }
    if (last < text.length) spans += InlineSpan(text.substring(last))
    return spans
}

private fun annotated(text: String): AnnotatedString = buildAnnotatedString {
    for (span in inlineSpans(text)) {
        val style = SpanStyle(
            fontWeight = if (span.bold) FontWeight.SemiBold else null,
            fontStyle = if (span.italic) androidx.compose.ui.text.font.FontStyle.Italic else null,
            fontFamily = if (span.code) FontFamily.Monospace else null,
        )
        if (style == SpanStyle()) append(span.text) else withStyle(style) { append(span.text) }
    }
}

/** Le note di una release, con il loro ritmo: titoli, elenchi puntati e numerati, citazioni. */
@Composable
fun ReleaseNotesText(markdown: String, modifier: Modifier = Modifier) {
    val blocks = releaseBlocks(markdown)
    if (blocks.isEmpty()) {
        Text("Nessuna nota per questa versione.", style = Theme.type.style(15), color = Theme.scheme.onSurfaceVariant, modifier = modifier)
        return
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (block in blocks) {
            when (block) {
                is ReleaseBlock.Heading -> {
                    val size = when (block.level) { 1 -> 20; 2 -> 17; else -> 15 }
                    Text(annotated(block.text), style = Theme.type.style(size, 600, rounded = true), color = Theme.scheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp))
                }
                is ReleaseBlock.Paragraph -> Text(annotated(block.text), style = Theme.type.style(15), color = Theme.scheme.onSurface)
                is ReleaseBlock.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("•", style = Theme.type.style(15, 600), color = Theme.scheme.primary)
                    Text(annotated(block.text), style = Theme.type.style(15), color = Theme.scheme.onSurface, modifier = Modifier.weight(1f))
                }
                is ReleaseBlock.Numbered -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${block.number}.", style = Theme.type.style(15, 600), color = Theme.scheme.primary)
                    Text(annotated(block.text), style = Theme.type.style(15), color = Theme.scheme.onSurface, modifier = Modifier.weight(1f))
                }
                is ReleaseBlock.Quote -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(Theme.scheme.outlineVariant, RoundedCornerShape(2.dp)))
                    Text(annotated(block.text), style = Theme.type.style(14), color = Theme.scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
