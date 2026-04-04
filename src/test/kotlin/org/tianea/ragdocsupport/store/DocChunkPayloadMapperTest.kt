package org.tianea.ragdocsupport.store

import io.kotest.matchers.shouldBe
import io.qdrant.client.PointIdFactory.id
import io.qdrant.client.ValueFactory.value
import io.qdrant.client.grpc.Points.ScoredPoint
import org.junit.jupiter.api.Test
import org.tianea.ragdocsupport.core.model.DocType
import org.tianea.ragdocsupport.fixture.aDocMetadata
import java.time.LocalDate
import java.util.UUID

class DocChunkPayloadMapperTest {
    @Test
    fun `toPayload maps all metadata fields`() {
        val metadata = aDocMetadata(
            library = "kafka",
            version = "3.7.0",
            docType = DocType.MIGRATION,
            section = "Breaking Changes",
            sectionPath = "Migration > Breaking Changes",
            sourceUrl = "https://kafka.apache.org/migration",
            indexedAt = LocalDate.of(2026, 3, 15),
            latest = false,
        )

        val payload = DocChunkPayloadMapper.toPayload(metadata, "some text")

        payload["library"]!!.stringValue shouldBe "kafka"
        payload["version"]!!.stringValue shouldBe "3.7.0"
        payload["doc_type"]!!.stringValue shouldBe "migration"
        payload["section"]!!.stringValue shouldBe "Breaking Changes"
        payload["section_path"]!!.stringValue shouldBe "Migration > Breaking Changes"
        payload["source_url"]!!.stringValue shouldBe "https://kafka.apache.org/migration"
        payload["indexed_at"]!!.stringValue shouldBe "2026-03-15"
        payload["latest"]!!.boolValue shouldBe false
        payload["text"]!!.stringValue shouldBe "some text"
    }

    @Test
    fun `fromScoredPoint converts payload back to DocChunk`() {
        val pointId = UUID.randomUUID()
        val scoredPoint = ScoredPoint.newBuilder()
            .setId(id(pointId))
            .putAllPayload(
                mapOf(
                    "library" to value("spring-boot"),
                    "version" to value("4.0.0"),
                    "doc_type" to value("reference"),
                    "section" to value("Configuration"),
                    "section_path" to value("Reference > Configuration"),
                    "source_url" to value("https://docs.spring.io"),
                    "indexed_at" to value("2026-01-01"),
                    "latest" to value(true),
                    "text" to value("Config documentation"),
                ),
            ).build()

        val chunk = DocChunkPayloadMapper.fromScoredPoint(scoredPoint)

        chunk.id shouldBe pointId
        chunk.text shouldBe "Config documentation"
        chunk.metadata.library shouldBe "spring-boot"
        chunk.metadata.version shouldBe "4.0.0"
        chunk.metadata.docType shouldBe DocType.REFERENCE
        chunk.metadata.section shouldBe "Configuration"
        chunk.metadata.sectionPath shouldBe "Reference > Configuration"
        chunk.metadata.sourceUrl shouldBe "https://docs.spring.io"
        chunk.metadata.indexedAt shouldBe LocalDate.of(2026, 1, 1)
        chunk.metadata.latest shouldBe true
    }

    @Test
    fun `fromScoredPoint uses defaults for missing fields`() {
        val pointId = UUID.randomUUID()
        val scoredPoint = ScoredPoint.newBuilder()
            .setId(id(pointId))
            .build()

        val chunk = DocChunkPayloadMapper.fromScoredPoint(scoredPoint)

        chunk.text shouldBe ""
        chunk.metadata.library shouldBe ""
        chunk.metadata.docType shouldBe DocType.REFERENCE
        chunk.metadata.latest shouldBe false
    }

    @Test
    fun `fromScoredPoint handles invalid doc_type gracefully`() {
        val pointId = UUID.randomUUID()
        val scoredPoint = ScoredPoint.newBuilder()
            .setId(id(pointId))
            .putPayload("doc_type", value("unknown_type"))
            .build()

        val chunk = DocChunkPayloadMapper.fromScoredPoint(scoredPoint)

        chunk.metadata.docType shouldBe DocType.REFERENCE
    }
}
