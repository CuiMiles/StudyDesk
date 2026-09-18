package io.github.cuimiles.studydesk

import androidx.compose.ui.graphics.Color
import io.github.cuimiles.studydesk.ui.components.MarkdownBlock
import io.github.cuimiles.studydesk.ui.components.parseMarkdownBlocks
import io.github.cuimiles.studydesk.ui.components.parseMarkdownInline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRendererTest {

    @Test
    fun testParseTableBlock() {
        val md = """
            | Word | Distinction |
            |---|---|
            | **king** | Rules a kingdom |
            | **emperor** | Rules an empire |
        """.trimIndent()

        val blocks = parseMarkdownBlocks(md)
        assertEquals(1, blocks.size)
        val table = blocks[0] as MarkdownBlock.Table
        assertEquals(listOf("Word", "Distinction"), table.headers)
        assertEquals(2, table.rows.size)
        assertEquals("**king**", table.rows[0][0])
        assertEquals("Rules a kingdom", table.rows[0][1])
        assertEquals("**emperor**", table.rows[1][0])
        assertEquals("Rules an empire", table.rows[1][1])
    }

    @Test
    fun testParseQuoteBlock() {
        val md = """
            > By the fourth century, the Roman world had split into rival courts.
            > Each general claimed the title for himself.
        """.trimIndent()

        val blocks = parseMarkdownBlocks(md)
        assertEquals(1, blocks.size)
        val quote = blocks[0] as MarkdownBlock.Quote
        assertTrue(quote.text.contains("By the fourth century"))
        assertTrue(quote.text.contains("Each general claimed"))
    }

    @Test
    fun testParseCalloutBlock() {
        val md = "**Core feeling:** singular, top-of-the-pyramid sovereignty."
        val blocks = parseMarkdownBlocks(md)
        assertEquals(1, blocks.size)
        val callout = blocks[0] as MarkdownBlock.Callout
        assertEquals("Core feeling", callout.title)
        assertEquals("singular, top-of-the-pyramid sovereignty.", callout.body)
    }

    @Test
    fun testParseListAndHeaders() {
        val md = """
            ### Step 3: Register Perception
            - First bullet point
            - Second bullet point
            1. Numbered point one
            2. Numbered point two
        """.trimIndent()

        val blocks = parseMarkdownBlocks(md)
        assertEquals(5, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Header)
        assertEquals("Step 3: Register Perception", (blocks[0] as MarkdownBlock.Header).text)
        assertTrue(blocks[1] is MarkdownBlock.BulletItem)
        assertTrue(blocks[2] is MarkdownBlock.BulletItem)
        assertTrue(blocks[3] is MarkdownBlock.NumberedItem)
        assertTrue(blocks[4] is MarkdownBlock.NumberedItem)
    }

    @Test
    fun testParseMarkdownInlineStripsSymbols() {
        val input = "This is **bold** and *italic* and `code` and ***both***."
        val annotated = parseMarkdownInline(
            text = input,
            baseColor = Color.Black,
            boldColor = Color.Blue,
            codeBgColor = Color.LightGray
        )
        // Ensure no raw asterisks or backticks are present in the final rendered text!
        val plain = annotated.text
        assertEquals("This is bold and italic and code and both.", plain)
    }
}
