package com.example.polygoneditor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import polygoneditor.shared.generated.resources.Res

private enum class HelpLanguage(val label: String, val file: String) {
    English("English", "files/help_ang.md"),
    Polish("Polski", "files/help_pl.md"),
}

/**
 * The in-app documentation: help_ang.md / help_pl.md from composeResources, rendered as text.
 * Opens in English; the buttons at the top switch the language.
 * Esc, a click outside or "Close" calls [onDismiss].
 */
@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    var language by remember { mutableStateOf(HelpLanguage.English) }

    // Read once per opening and language; null while loading
    val blocks by produceState<List<MdBlock>?>(null, language) {
        value = null
        value = parseMarkdown(Res.readBytes(language.file).decodeToString())
    }

    // The default width of a dialog is too narrow for the tables
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.widthIn(max = 900.dp).fillMaxWidth(0.9f).fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (option in HelpLanguage.entries) {
                        if (option == language) {
                            Button(onClick = {}) { Text(option.label) }
                        } else {
                            OutlinedButton(onClick = { language = option }) { Text(option.label) }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Box(Modifier.weight(1f)) {
                    val content = blocks
                    if (content == null) {
                        Text("Loading…", Modifier.align(Alignment.Center))
                    } else {
                        SelectionContainer {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(content) { MarkdownBlock(it) }
                            }
                        }
                    }
                }
                HorizontalDivider()
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
            }
        }
    }
}

@Composable
private fun MarkdownBlock(block: MdBlock) {
    when (block) {
        is MdBlock.Heading -> Text(
            inline(block.text),
            Modifier.padding(top = if (block.level <= 2) 12.dp else 6.dp),
            style = when (block.level) {
                1 -> MaterialTheme.typography.headlineSmall
                2 -> MaterialTheme.typography.titleLarge
                else -> MaterialTheme.typography.titleMedium
            },
        )

        is MdBlock.Paragraph -> Text(inline(block.text))

        is MdBlock.ListItem -> Row(Modifier.padding(start = (12 + 16 * block.indent).dp)) {
            Text(block.marker, Modifier.widthIn(min = 20.dp))
            Text(inline(block.text))
        }

        is MdBlock.Table -> MarkdownTable(block.rows)

        is MdBlock.Code -> Text(
            block.lines.joinToString("\n"),
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                .padding(8.dp),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

// Equal-width columns, header row on a darker background
@Composable
private fun MarkdownTable(rows: List<List<String>>) {
    val columns = rows.maxOf { it.size }
    val border = MaterialTheme.colorScheme.outlineVariant

    Column(Modifier.fillMaxWidth().border(1.dp, border)) {
        rows.forEachIndexed { r, row ->
            // IntrinsicSize: every cell as tall as the tallest one, so the backgrounds line up
            Row(
                Modifier
                    .height(IntrinsicSize.Min)
                    .then(if (r == 0) Modifier.background(MaterialTheme.colorScheme.surfaceVariant) else Modifier)
            ) {
                for (c in 0 until columns) {
                    Text(
                        inline(row.getOrElse(c) { "" }),
                        Modifier.weight(1f).fillMaxSize().border(0.5.dp, border).padding(6.dp),
                        fontWeight = if (r == 0) FontWeight.Bold else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun inline(text: String): AnnotatedString {
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    return buildAnnotatedString {
        for (span in parseInline(text)) {
            when (span) {
                is MdSpan.Plain -> append(span.text)
                is MdSpan.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) }
                is MdSpan.Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) {
                    append(span.text)
                }
            }
        }
    }
}
