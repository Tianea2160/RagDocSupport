package org.tianea.ragdocsupport.benchmark

import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.tianea.ragdocsupport.core.port.VectorStore
import org.tianea.ragdocsupport.sync.BulkRegisterRequest
import org.tianea.ragdocsupport.sync.DocSyncService

@Tag("benchmark")
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class RagBenchmarkTest {
    @Autowired
    lateinit var syncService: DocSyncService

    @Autowired
    lateinit var vectorStore: VectorStore

    @Autowired
    lateinit var embeddingModel: EmbeddingModel

    @Test
    @Order(1)
    fun `setup - bulk register hanpyo dependencies`() {
        val libraries = listOf(
            BulkRegisterRequest("spring-boot", "4.0.4"),
            BulkRegisterRequest("spring-framework", "7.0.4"),
            BulkRegisterRequest("spring-security", "7.0.1"),
            BulkRegisterRequest("spring-data-redis", "4.0.1"),
            BulkRegisterRequest("jooq", "3.20.11"),
            BulkRegisterRequest("kotlinx-coroutines", "1.10.2"),
            BulkRegisterRequest("liquibase", "4.32.0"),
            BulkRegisterRequest("springdoc-openapi", "3.0.1"),
            BulkRegisterRequest("testcontainers", "1.21.0"),
            BulkRegisterRequest("postgresql", "42.7.7"),
        )

        println("=== BENCHMARK SETUP: Bulk Register ===")
        val result = syncService.registerBulk(libraries)

        println("Total chunks indexed: ${result.totalChunks}")
        println("Successes: ${result.successCount} / ${result.entries.size}")
        println("Failures: ${result.failureCount}")

        for (entry in result.entries) {
            val status = if (entry.result.success) "OK" else "FAIL"
            println("  [$status] ${entry.library}:${entry.version} — ${entry.result.chunksIndexed} chunks")
            for (failed in entry.result.failedDocTypes) {
                println("    ⚠ ${failed.docType}: ${failed.triedUrls.joinToString(", ")}")
            }
        }
    }

    @Test
    @Order(2)
    fun `benchmark - run search queries and report results`() {
        val dataset = BenchmarkDatasetLoader.load()
        val results = mutableListOf<BenchmarkResult>()

        for (benchmarkQuery in dataset.queries) {
            val startTime = System.currentTimeMillis()
            val queryEmbedding = embeddingModel.embed(benchmarkQuery.query)
            val chunks = vectorStore.search(
                query = queryEmbedding,
                library = benchmarkQuery.library,
                limit = 5,
            )
            val elapsed = System.currentTimeMillis() - startTime

            results.add(BenchmarkResult(benchmarkQuery, chunks, elapsed))
        }

        val report = BenchmarkReport(results)
        println(report.formatReport())
    }
}
