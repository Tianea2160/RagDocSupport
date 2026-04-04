package org.tianea.ragdocsupport.crawler

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class DocChunkerTest {
    private val chunker = DocChunker()

    @Test
    fun `chunks by top-level sections`() {
        val markdown = """
            |## Section A
            |Content A
            |
            |## Section B
            |Content B
        """.trimMargin()

        val chunks = chunker.chunk(markdown)

        chunks shouldHaveSize 2
        chunks[0].section shouldBe "Section A"
        chunks[0].text shouldContain "Content A"
        chunks[1].section shouldBe "Section B"
        chunks[1].text shouldContain "Content B"
    }

    @Test
    fun `chunks by h1 sections`() {
        val markdown = """
            |# Title
            |Intro text
            |
            |# Another Title
            |More text
        """.trimMargin()

        val chunks = chunker.chunk(markdown)

        chunks shouldHaveSize 2
        chunks[0].section shouldBe "Title"
        chunks[1].section shouldBe "Another Title"
    }

    @Test
    fun `splits large sections by sub-headers`() {
        val longContent = "x".repeat(600)
        val markdown = """
            |## Parent
            |$longContent
            |### Sub A
            |$longContent
            |### Sub B
            |$longContent
        """.trimMargin()

        val chunks = chunker.chunk(markdown)

        chunks.size shouldBeGreaterThan 1
        chunks.any { it.sectionPath.contains("Sub A") } shouldBe true
        chunks.any { it.sectionPath.contains("Sub B") } shouldBe true
    }

    @Test
    fun `applies fixed-size chunking with overlap for very large sections`() {
        val hugeText = "word ".repeat(300) // ~1500 characters
        val markdown = "## Big Section\n$hugeText"

        val chunks = chunker.chunk(markdown)

        chunks.size shouldBeGreaterThan 1
        chunks.all { it.section == "Big Section" } shouldBe true
        chunks.all { it.text.length <= 1000 } shouldBe true
    }

    @Test
    fun `filters out blank chunks`() {
        val markdown = """
            |## Empty
            |
            |
            |## Content
            |Real content here
        """.trimMargin()

        val chunks = chunker.chunk(markdown)

        chunks.none { it.text.isBlank() } shouldBe true
    }

    @Test
    fun `returns empty list for blank input`() {
        chunker.chunk("").shouldBeEmpty()
        chunker.chunk("   ").shouldBeEmpty()
    }

    @Test
    fun `builds section path correctly`() {
        val markdown = """
            |## Parent Section
            |Some content
        """.trimMargin()

        val chunks = chunker.chunk(markdown)

        chunks[0].sectionPath shouldBe "Parent Section"
    }

    @Test
    fun `small section stays as single chunk`() {
        val markdown = """
            |## Small
            |Short content
        """.trimMargin()

        val chunks = chunker.chunk(markdown)

        chunks shouldHaveSize 1
        chunks[0].text shouldContain "Short content"
    }
}
