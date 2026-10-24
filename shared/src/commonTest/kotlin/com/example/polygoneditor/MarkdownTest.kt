package com.example.polygoneditor

import com.example.polygoneditor.ui.MdBlock
import com.example.polygoneditor.ui.MdSpan
import com.example.polygoneditor.ui.parseInline
import com.example.polygoneditor.ui.parseMarkdown
import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownTest {
    @Test
    fun headingsKeepTheirLevel() {
        assertEquals(
            listOf(MdBlock.Heading(1, "Title"), MdBlock.Heading(3, "Sub")),
            parseMarkdown("# Title\n### Sub"),
        )
    }

    @Test
    fun consecutiveLinesMakeOneParagraph() {
        assertEquals(
            listOf(MdBlock.Paragraph("one two"), MdBlock.Paragraph("three")),
            parseMarkdown("one\ntwo\n\nthree"),
        )
    }

    @Test
    fun listItemsWithMarkersAndNesting() {
        assertEquals(
            listOf(
                MdBlock.ListItem("•", "a", 0),
                MdBlock.ListItem("•", "nested", 1),
                MdBlock.ListItem("2.", "second", 0),
            ),
            parseMarkdown("- a\n  - nested\n2. second"),
        )
    }

    @Test
    fun tableSkipsTheSeparatorRow() {
        assertEquals(
            listOf(MdBlock.Table(listOf(listOf("Key", "Action"), listOf("F1", "help")))),
            parseMarkdown("| Key | Action |\n|---|---|\n| F1 | help |"),
        )
    }

    @Test
    fun paragraphRightAfterATableIsNotLost() {
        assertEquals(
            listOf(MdBlock.Table(listOf(listOf("a"))), MdBlock.Paragraph("after")),
            parseMarkdown("| a |\nafter"),
        )
    }

    @Test
    fun codeBlockKeepsLinesAsTheyAre() {
        assertEquals(
            listOf(MdBlock.Code(listOf("  x = 1", "# not a heading")), MdBlock.Paragraph("text")),
            parseMarkdown("```\n  x = 1\n# not a heading\n```\ntext"),
        )
    }

    @Test
    fun inlineBoldAndCode() {
        assertEquals(
            listOf(MdSpan.Plain("a "), MdSpan.Bold("b"), MdSpan.Plain(" "), MdSpan.Code("c()"), MdSpan.Plain(".")),
            parseInline("a **b** `c()`."),
        )
    }

    @Test
    fun unclosedMarkerStaysPlain() {
        assertEquals(listOf(MdSpan.Plain("2 ** 3 and `x")), parseInline("2 ** 3 and `x"))
    }
}
