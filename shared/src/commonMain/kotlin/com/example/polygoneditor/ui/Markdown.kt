package com.example.polygoneditor.ui

// The subset of Markdown used by help_ang.md / help_pl.md – no Compose here, so it can be unit tested
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock

    // marker: "•" for "- ", "1." for "1. "; indent: nesting level (2 spaces each)
    data class ListItem(val marker: String, val text: String, val indent: Int) : MdBlock

    // The first row is the header
    data class Table(val rows: List<List<String>>) : MdBlock
    data class Code(val lines: List<String>) : MdBlock
}

// A piece of a line: plain, **bold** or `code`
sealed interface MdSpan {
    val text: String

    data class Plain(override val text: String) : MdSpan
    data class Bold(override val text: String) : MdSpan
    data class Code(override val text: String) : MdSpan
}

private val NumberedItem = Regex("""^(\d+)\.\s+(.*)$""")
private val TableSeparator = Regex("""^\|[\s:|-]+\|$""")

fun parseMarkdown(text: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = mutableListOf<String>()
    val lines = text.lines()
    var i = 0

    // Consecutive text lines make one paragraph – it ends at a blank line or another block
    fun flushParagraph() {
        if (paragraph.isNotEmpty()) blocks += MdBlock.Paragraph(paragraph.joinToString(" "))
        paragraph.clear()
    }

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        val indent = (line.length - line.trimStart().length) / 2

        when {
            trimmed.startsWith("```") -> {
                flushParagraph()
                val code = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) code += lines[i++]
                blocks += MdBlock.Code(code)
            }

            trimmed.startsWith("|") -> {
                flushParagraph()
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    val row = lines[i].trim()
                    if (!TableSeparator.matches(row)) rows += row.removePrefix("|").removeSuffix("|").split("|").map { it.trim() }
                    i++
                }
                blocks += MdBlock.Table(rows)
                continue // i already points at the line after the table
            }

            trimmed.startsWith("#") -> {
                flushParagraph()
                val level = trimmed.takeWhile { it == '#' }.length
                blocks += MdBlock.Heading(level, trimmed.drop(level).trim())
            }

            trimmed.startsWith("- ") -> {
                flushParagraph()
                blocks += MdBlock.ListItem("•", trimmed.drop(2), indent)
            }

            NumberedItem.matches(trimmed) -> {
                flushParagraph()
                val (number, rest) = NumberedItem.find(trimmed)!!.destructured
                blocks += MdBlock.ListItem("$number.", rest, indent)
            }

            trimmed.isEmpty() -> flushParagraph()

            else -> paragraph += trimmed
        }
        i++
    }
    flushParagraph()
    return blocks
}

// Splits a line into **bold**, `code` and plain spans; an unclosed marker stays as plain text
fun parseInline(text: String): List<MdSpan> {
    val spans = mutableListOf<MdSpan>()
    val plain = StringBuilder()
    var i = 0

    fun flushPlain() {
        if (plain.isNotEmpty()) spans += MdSpan.Plain(plain.toString())
        plain.clear()
    }

    while (i < text.length) {
        val marker = when {
            text.startsWith("**", i) -> "**"
            text[i] == '`' -> "`"
            else -> null
        }
        val end = marker?.let { text.indexOf(it, i + it.length) } ?: -1

        if (marker == null || end < 0) {
            plain.append(text[i++])
            continue
        }

        flushPlain()
        val inner = text.substring(i + marker.length, end)
        spans += if (marker == "`") MdSpan.Code(inner) else MdSpan.Bold(inner)
        i = end + marker.length
    }
    flushPlain()
    return spans
}
