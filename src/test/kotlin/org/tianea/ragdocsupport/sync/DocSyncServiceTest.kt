package org.tianea.ragdocsupport.sync

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.tianea.ragdocsupport.core.model.DocType
import org.tianea.ragdocsupport.core.model.DocUrlPattern
import org.tianea.ragdocsupport.core.port.DocSourceRepository
import org.tianea.ragdocsupport.core.port.LibraryIndexInfo
import org.tianea.ragdocsupport.core.port.VectorStore
import org.tianea.ragdocsupport.fixture.aDocChunk
import org.tianea.ragdocsupport.fixture.aDocSource
import org.tianea.ragdocsupport.fixture.anEmbedding

class DocSyncServiceTest {
    private val docProcessor = mockk<DocProcessor>()
    private val vectorStore = mockk<VectorStore>()
    private val docSourceRepository = mockk<DocSourceRepository>()

    private lateinit var service: DocSyncService

    @BeforeEach
    fun setUp() {
        service = DocSyncService(docProcessor, vectorStore, docSourceRepository)
    }

    @Test
    fun `register returns failure when no URL candidates found`() {
        every { docSourceRepository.findByLibrary("unknown") } returns null

        val result = service.register("unknown", "1.0.0")

        result.success shouldBe false
    }

    @Test
    fun `register with explicit docUrl bypasses source repository`() {
        justRun { vectorStore.ensureCollection() }
        every { vectorStore.listIndexedLibraries() } returns emptyList()
        justRun { vectorStore.deleteByLibraryAndVersion(any(), any()) }
        justRun { vectorStore.upsert(any()) }

        val chunks = listOf(aDocChunk(embedding = anEmbedding()))
        every {
            docProcessor.processRecursive("mylib", "1.0", DocType.REFERENCE, listOf("https://custom.com/docs"), 2, any())
        } returns chunks

        val result = service.register("mylib", "1.0", "https://custom.com/docs")

        result.success shouldBe true
        result.chunksIndexed shouldBe 1
        verify(exactly = 0) { docSourceRepository.findByLibrary(any()) }
    }

    @Test
    fun `register marks existing latest versions as non-latest`() {
        justRun { vectorStore.ensureCollection() }
        every { vectorStore.listIndexedLibraries() } returns listOf(
            LibraryIndexInfo("spring-boot", "3.0.0", 50, latest = true),
        )
        justRun { vectorStore.updateLatestFlag("spring-boot", "3.0.0", false) }
        justRun { vectorStore.deleteByLibraryAndVersion(any(), any()) }
        justRun { vectorStore.upsert(any()) }

        every { docSourceRepository.findByLibrary("spring-boot") } returns aDocSource(
            docs = mapOf(
                DocType.REFERENCE to DocUrlPattern("https://docs.spring.io/{version}/ref"),
            ),
        )
        every {
            docProcessor.processRecursive(any(), any(), any(), any(), any(), any())
        } returns listOf(aDocChunk(embedding = anEmbedding()))

        service.register("spring-boot", "4.0.0")

        verify { vectorStore.updateLatestFlag("spring-boot", "3.0.0", false) }
    }

    @Test
    fun `register records failed doc types`() {
        justRun { vectorStore.ensureCollection() }
        every { vectorStore.listIndexedLibraries() } returns emptyList()
        justRun { vectorStore.deleteByLibraryAndVersion(any(), any()) }

        every { docSourceRepository.findByLibrary("kafka") } returns aDocSource(
            library = "kafka",
            docs = mapOf(
                DocType.REFERENCE to DocUrlPattern("https://kafka.apache.org/{version}/ref"),
                DocType.MIGRATION to DocUrlPattern("https://kafka.apache.org/{version}/migration"),
            ),
        )
        every {
            docProcessor.processRecursive("kafka", "3.7.0", DocType.REFERENCE, any(), 2, any())
        } returns listOf(aDocChunk(embedding = anEmbedding()))
        every {
            docProcessor.processRecursive("kafka", "3.7.0", DocType.MIGRATION, any(), 2, any())
        } returns null
        justRun { vectorStore.upsert(any()) }

        val result = service.register("kafka", "3.7.0")

        result.success shouldBe true
        result.failedDocTypes shouldHaveSize 1
        result.failedDocTypes[0].docType shouldBe DocType.MIGRATION
    }

    @Test
    fun `registerBulk registers all libraries and aggregates results`() {
        justRun { vectorStore.ensureCollection() }
        every { vectorStore.listIndexedLibraries() } returns emptyList()
        justRun { vectorStore.deleteByLibraryAndVersion(any(), any()) }
        justRun { vectorStore.upsert(any()) }

        every { docSourceRepository.findByLibrary("lib-a") } returns aDocSource(
            library = "lib-a",
            docs = mapOf(DocType.REFERENCE to DocUrlPattern("https://example.com/a/{version}")),
        )
        every { docSourceRepository.findByLibrary("lib-b") } returns null

        every {
            docProcessor.processRecursive("lib-a", "1.0", DocType.REFERENCE, any(), 2, any())
        } returns listOf(aDocChunk(embedding = anEmbedding()), aDocChunk(embedding = anEmbedding()))

        val result = service.registerBulk(
            listOf(
                BulkRegisterRequest("lib-a", "1.0"),
                BulkRegisterRequest("lib-b", "2.0"),
            ),
        )

        result.entries shouldHaveSize 2
        result.successCount shouldBe 1
        result.failureCount shouldBe 1
        result.totalChunks shouldBe 2
    }

    @Test
    fun `register delegates recursive patterns to processRecursive`() {
        justRun { vectorStore.ensureCollection() }
        every { vectorStore.listIndexedLibraries() } returns emptyList()
        justRun { vectorStore.deleteByLibraryAndVersion(any(), any()) }
        justRun { vectorStore.upsert(any()) }

        every { docSourceRepository.findByLibrary("spring-boot") } returns aDocSource(
            docs = mapOf(
                DocType.GUIDE to DocUrlPattern(
                    urlTemplates = listOf("https://docs.spring.io/{version}/guide"),
                    recursive = true,
                    maxDepth = 3,
                ),
            ),
        )
        every {
            docProcessor.processRecursive("spring-boot", "4.0.0", DocType.GUIDE, any(), 3, any())
        } returns listOf(aDocChunk(embedding = anEmbedding()))

        val result = service.register("spring-boot", "4.0.0")

        result.success shouldBe true
        verify { docProcessor.processRecursive("spring-boot", "4.0.0", DocType.GUIDE, any(), 3, any()) }
    }
}
