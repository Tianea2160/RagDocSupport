package org.tianea.ragdocsupport.core.model

import java.time.LocalDate
import java.util.UUID

data class DocChunk(
    val id: UUID = UUID.randomUUID(),
    val text: String,
    val metadata: DocMetadata,
    val embedding: FloatArray? = null,
) {
    override fun equals(other: Any?): Boolean = other is DocChunk && id == other.id

    override fun hashCode(): Int = id.hashCode()
}

data class DocMetadata(
    val library: String,
    val version: String,
    val docType: DocType,
    val section: String,
    val sectionPath: String,
    val sourceUrl: String,
    val indexedAt: LocalDate = LocalDate.now(),
    val latest: Boolean = true,
)

enum class DocType {
    REFERENCE,
    MIGRATION,
    CHANGELOG,
    GUIDE,
}
