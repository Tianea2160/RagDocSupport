package org.tianea.ragdocsupport.store

import io.qdrant.client.ValueFactory.value
import io.qdrant.client.grpc.JsonWithInt
import io.qdrant.client.grpc.Points.ScoredPoint
import org.tianea.ragdocsupport.core.model.DocChunk
import org.tianea.ragdocsupport.core.model.DocMetadata
import org.tianea.ragdocsupport.core.model.DocType
import java.time.LocalDate
import java.util.UUID

object DocChunkPayloadMapper {
    const val FIELD_LIBRARY = "library"
    const val FIELD_VERSION = "version"
    const val FIELD_DOC_TYPE = "doc_type"
    const val FIELD_SECTION = "section"
    const val FIELD_SECTION_PATH = "section_path"
    const val FIELD_SOURCE_URL = "source_url"
    const val FIELD_INDEXED_AT = "indexed_at"
    const val FIELD_LATEST = "latest"
    const val FIELD_TEXT = "text"

    fun toPayload(
        metadata: DocMetadata,
        text: String,
    ): Map<String, JsonWithInt.Value> = mapOf(
        FIELD_LIBRARY to value(metadata.library),
        FIELD_VERSION to value(metadata.version),
        FIELD_DOC_TYPE to value(metadata.docType.name.lowercase()),
        FIELD_SECTION to value(metadata.section),
        FIELD_SECTION_PATH to value(metadata.sectionPath),
        FIELD_SOURCE_URL to value(metadata.sourceUrl),
        FIELD_INDEXED_AT to value(metadata.indexedAt.toString()),
        FIELD_LATEST to value(metadata.latest),
        FIELD_TEXT to value(text),
    )

    fun fromScoredPoint(point: ScoredPoint): DocChunk {
        val payload = point.payloadMap
        return DocChunk(
            id = UUID.fromString(point.id.uuid),
            text = payload[FIELD_TEXT]?.stringValue ?: "",
            metadata =
            DocMetadata(
                library = payload[FIELD_LIBRARY]?.stringValue ?: "",
                version = payload[FIELD_VERSION]?.stringValue ?: "",
                docType =
                runCatching {
                    DocType.valueOf(
                        (payload[FIELD_DOC_TYPE]?.stringValue ?: "reference").uppercase(),
                    )
                }.getOrDefault(DocType.REFERENCE),
                section = payload[FIELD_SECTION]?.stringValue ?: "",
                sectionPath = payload[FIELD_SECTION_PATH]?.stringValue ?: "",
                sourceUrl = payload[FIELD_SOURCE_URL]?.stringValue ?: "",
                indexedAt =
                runCatching {
                    LocalDate.parse(payload[FIELD_INDEXED_AT]?.stringValue ?: "")
                }.getOrDefault(LocalDate.now()),
                latest = payload[FIELD_LATEST]?.boolValue ?: false,
            ),
        )
    }
}
