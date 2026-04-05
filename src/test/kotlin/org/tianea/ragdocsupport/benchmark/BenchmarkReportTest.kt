package org.tianea.ragdocsupport.benchmark

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.tianea.ragdocsupport.fixture.aDocChunk
import org.tianea.ragdocsupport.fixture.aDocMetadata

class BenchmarkReportTest {
    private val sampleQuery = BenchmarkQuery(
        query = "How to configure Spring Boot?",
        library = "spring-boot",
        category = "configuration",
        difficulty = "easy",
    )

    @Test
    fun `hitRate is 100 percent when all queries have results`() {
        val report = BenchmarkReport(
            listOf(
                BenchmarkResult(sampleQuery, listOf(aDocChunk()), 10),
                BenchmarkResult(sampleQuery, listOf(aDocChunk()), 15),
            ),
        )

        report.hitRate shouldBe 1.0
        report.queriesWithResults shouldBe 2
    }

    @Test
    fun `hitRate is 0 when no queries have results`() {
        val report = BenchmarkReport(
            listOf(
                BenchmarkResult(sampleQuery, emptyList(), 5),
            ),
        )

        report.hitRate shouldBe 0.0
    }

    @Test
    fun `formatReport contains query and chunk info`() {
        val chunk = aDocChunk(
            text = "Spring Boot auto-configuration...",
            metadata = aDocMetadata(sectionPath = "Reference > Auto-Config"),
        )
        val report = BenchmarkReport(
            listOf(BenchmarkResult(sampleQuery, listOf(chunk), 12)),
        )

        val output = report.formatReport()

        output shouldContain "How to configure Spring Boot?"
        output shouldContain "spring-boot"
        output shouldContain "Reference > Auto-Config"
        output shouldContain "Hit rate:"
    }

    @Test
    fun `formatReport shows NO RESULTS for empty results`() {
        val report = BenchmarkReport(
            listOf(BenchmarkResult(sampleQuery, emptyList(), 5)),
        )

        report.formatReport() shouldContain "[NO RESULTS]"
    }
}
