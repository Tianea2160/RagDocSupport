package org.tianea.ragdocsupport.mcp

import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.ai.embedding.EmbeddingModel
import org.tianea.ragdocsupport.core.port.VectorStore
import org.tianea.ragdocsupport.fixture.aDocChunk
import org.tianea.ragdocsupport.fixture.aDocMetadata
import org.tianea.ragdocsupport.fixture.anEmbedding

class DocsSearchToolTest {
    private val vectorStore = mockk<VectorStore>()
    private val embeddingModel = mockk<EmbeddingModel>()
    private val tool = DocsSearchTool(vectorStore, embeddingModel)

    @Test
    fun `returns formatted results`() {
        val embedding = anEmbedding()
        every { embeddingModel.embed(any<String>()) } returns embedding
        every { vectorStore.search(embedding, "spring-boot", null, 5) } returns listOf(
            aDocChunk(
                text = "Spring Boot auto-configuration",
                metadata = aDocMetadata(
                    library = "spring-boot",
                    version = "4.0.0",
                    section = "Auto-config",
                    sectionPath = "Reference > Auto-config",
                    sourceUrl = "https://docs.spring.io/auto-config",
                ),
            ),
        )

        val result = tool.docsSearch("auto configuration", "spring-boot", null, null)

        result shouldContain "Result 1"
        result shouldContain "spring-boot v4.0.0"
        result shouldContain "Reference > Auto-config"
        result shouldContain "Spring Boot auto-configuration"
    }

    @Test
    fun `returns no results message when empty`() {
        every { embeddingModel.embed(any<String>()) } returns anEmbedding()
        every { vectorStore.search(any(), any(), any(), any()) } returns emptyList()

        val result = tool.docsSearch("nonexistent topic", "spring-boot", "4.0.0", null)

        result shouldContain "No results found"
        result shouldContain "spring-boot"
        result shouldContain "4.0.0"
    }

    @Test
    fun `uses custom limit`() {
        every { embeddingModel.embed(any<String>()) } returns anEmbedding()
        every { vectorStore.search(any(), null, null, 10) } returns emptyList()

        tool.docsSearch("query", null, null, 10)
    }
}
