package org.tianea.ragdocsupport.mcp

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.tianea.ragdocsupport.core.port.LibraryIndexInfo
import org.tianea.ragdocsupport.core.port.VectorStore

class DocsListToolTest {
    private val vectorStore = mockk<VectorStore>()
    private val tool = DocsListTool(vectorStore)

    @Test
    fun `returns formatted table of indexed libraries`() {
        every { vectorStore.listIndexedLibraries() } returns listOf(
            LibraryIndexInfo("spring-boot", "4.0.0", 100, latest = true),
            LibraryIndexInfo("spring-boot", "3.0.0", 80, latest = false),
            LibraryIndexInfo("kafka", "3.7.0", 50, latest = true),
        )

        val result = tool.docsList()

        result shouldContain "Indexed Documentation"
        result shouldContain "| spring-boot | 4.0.0 | 100 | Yes |"
        result shouldContain "| spring-boot | 3.0.0 | 80 |  |"
        result shouldContain "| kafka | 3.7.0 | 50 | Yes |"
    }

    @Test
    fun `returns empty message when no libraries indexed`() {
        every { vectorStore.listIndexedLibraries() } returns emptyList()

        val result = tool.docsList()

        result shouldContain "No documentation has been indexed"
        result shouldContain "docs-register"
    }

    @Test
    fun `groups by library name`() {
        every { vectorStore.listIndexedLibraries() } returns listOf(
            LibraryIndexInfo("spring-boot", "4.0.0", 100, latest = true),
            LibraryIndexInfo("spring-boot", "3.0.0", 80, latest = false),
        )

        val result = tool.docsList()

        result shouldContain "spring-boot"
        result shouldNotContain "| Library | Version |.*\n.*| Library | Version |"
    }
}
