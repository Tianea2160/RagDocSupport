package org.tianea.ragdocsupport.fixture

import org.tianea.ragdocsupport.core.model.DocChunk
import org.tianea.ragdocsupport.core.model.DocMetadata
import org.tianea.ragdocsupport.core.model.DocType
import java.time.LocalDate
import java.util.UUID

fun aDocChunk(
    id: UUID = UUID.randomUUID(),
    text: String = "Sample documentation text",
    metadata: DocMetadata = aDocMetadata(),
    embedding: FloatArray? = null,
) = DocChunk(id = id, text = text, metadata = metadata, embedding = embedding)

fun aDocMetadata(
    library: String = "spring-boot",
    version: String = "4.0.0",
    docType: DocType = DocType.REFERENCE,
    section: String = "Getting Started",
    sectionPath: String = "Getting Started",
    sourceUrl: String = "https://docs.spring.io/spring-boot/reference/4.0.0",
    indexedAt: LocalDate = LocalDate.of(2026, 1, 1),
    latest: Boolean = true,
) = DocMetadata(
    library = library,
    version = version,
    docType = docType,
    section = section,
    sectionPath = sectionPath,
    sourceUrl = sourceUrl,
    indexedAt = indexedAt,
    latest = latest,
)

fun anEmbedding(size: Int = 4096): FloatArray = FloatArray(size) { it.toFloat() / size }
