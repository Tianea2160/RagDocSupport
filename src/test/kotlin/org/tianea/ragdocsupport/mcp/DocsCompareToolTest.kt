package org.tianea.ragdocsupport.mcp

import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.ai.embedding.EmbeddingModel
import org.tianea.ragdocsupport.core.model.DocType
import org.tianea.ragdocsupport.core.port.VectorStore
import org.tianea.ragdocsupport.fixture.aDocChunk
import org.tianea.ragdocsupport.fixture.aDocMetadata
import org.tianea.ragdocsupport.fixture.anEmbedding

class DocsCompareToolTest {
    private val vectorStore = mockk<VectorStore>()
    private val embeddingModel = mockk<EmbeddingModel>()
    private val tool = DocsCompareTool(vectorStore, embeddingModel)

    @Test
    fun `returns comparison grouped by version`() {
        every { embeddingModel.embed(any<String>()) } returns anEmbedding()
        every {
            vectorStore.searchByVersions(any(), "spring-boot", listOf("3.0.0", "4.0.0"), 10)
        } returns listOf(
            aDocChunk(
                text = "Old security config",
                metadata = aDocMetadata(version = "3.0.0", section = "Security", docType = DocType.REFERENCE),
            ),
            aDocChunk(
                text = "New security config with defaults",
                metadata = aDocMetadata(version = "4.0.0", section = "Security", docType = DocType.MIGRATION),
            ),
        )

        val result = tool.docsCompare("spring-boot", "3.0.0", "4.0.0", "security changes")

        result shouldContain "Comparison: spring-boot 3.0.0 → 4.0.0"
        result shouldContain "Version 4.0.0"
        result shouldContain "Version 3.0.0"
        result shouldContain "[MIGRATION]"
        result shouldContain "[REFERENCE]"
    }

    @Test
    fun `returns not found message when no results`() {
        every { embeddingModel.embed(any<String>()) } returns anEmbedding()
        every { vectorStore.searchByVersions(any(), any(), any(), any()) } returns emptyList()

        val result = tool.docsCompare("unknown", "1.0", "2.0", null)

        result shouldContain "No comparison results found"
        result shouldContain "docs-register"
    }

    @Test
    fun `uses default query when none provided`() {
        every { embeddingModel.embed(any<String>()) } returns anEmbedding()
        every { vectorStore.searchByVersions(any(), any(), any(), any()) } returns emptyList()

        val result = tool.docsCompare("spring-boot", "3.0.0", "4.0.0", null)

        result shouldContain "No comparison results found"
    }
}
