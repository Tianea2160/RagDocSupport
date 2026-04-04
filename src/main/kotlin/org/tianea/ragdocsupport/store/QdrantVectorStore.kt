package org.tianea.ragdocsupport.store

import io.qdrant.client.ConditionFactory.filter
import io.qdrant.client.ConditionFactory.matchKeyword
import io.qdrant.client.PointIdFactory.id
import io.qdrant.client.QdrantClient
import io.qdrant.client.ValueFactory.value
import io.qdrant.client.VectorsFactory.vectors
import io.qdrant.client.WithPayloadSelectorFactory.enable
import io.qdrant.client.grpc.Collections.Distance
import io.qdrant.client.grpc.Collections.VectorParams
import io.qdrant.client.grpc.Common.Filter
import io.qdrant.client.grpc.Common.PointId
import io.qdrant.client.grpc.Points.PointStruct
import io.qdrant.client.grpc.Points.ScoredPoint
import io.qdrant.client.grpc.Points.ScrollPoints
import io.qdrant.client.grpc.Points.SearchPoints
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.tianea.ragdocsupport.config.QdrantProperties
import org.tianea.ragdocsupport.core.model.DocChunk
import org.tianea.ragdocsupport.core.model.DocMetadata
import org.tianea.ragdocsupport.core.model.DocType
import org.tianea.ragdocsupport.core.port.LibraryIndexInfo
import org.tianea.ragdocsupport.core.port.VectorStore
import java.time.LocalDate
import java.util.UUID

@Component
class QdrantVectorStore(
    private val client: QdrantClient,
    private val properties: QdrantProperties,
) : VectorStore {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val VECTOR_SIZE = 768 // nomic-embed-text default
    }

    override fun ensureCollection() {
        val collections = client.listCollectionsAsync().get()
        if (collections.none { it == properties.collectionName }) {
            client
                .createCollectionAsync(
                    properties.collectionName,
                    VectorParams
                        .newBuilder()
                        .setDistance(Distance.Cosine)
                        .setSize(VECTOR_SIZE.toLong())
                        .build(),
                ).get()
            log.info("Created Qdrant collection: ${properties.collectionName}")
        }
    }

    override fun upsert(chunks: List<DocChunk>) {
        if (chunks.isEmpty()) return
        val points =
            chunks.map { chunk ->
                val embedding = chunk.embedding ?: throw IllegalArgumentException("Embedding is required for upsert")
                PointStruct
                    .newBuilder()
                    .setId(id(chunk.id))
                    .setVectors(vectors(embedding.toList()))
                    .putAllPayload(chunk.metadata.toPayload(chunk.text))
                    .build()
            }

        client.upsertAsync(properties.collectionName, points).get()

        val meta = chunks.first().metadata
        log.info("Upserted ${chunks.size} chunks for ${meta.library}:${meta.version}")
    }

    override fun search(
        query: FloatArray,
        library: String?,
        version: String?,
        limit: Int,
    ): List<DocChunk> {
        val filterBuilder = Filter.newBuilder()
        if (library != null) {
            filterBuilder.addMust(matchKeyword("library", library))
        }
        if (version != null) {
            filterBuilder.addMust(matchKeyword("version", version))
        }

        val results =
            client
                .searchAsync(
                    SearchPoints
                        .newBuilder()
                        .setCollectionName(properties.collectionName)
                        .addAllVector(query.toList())
                        .setFilter(filterBuilder.build())
                        .setLimit(limit.toLong())
                        .setWithPayload(enable(true))
                        .build(),
                ).get()

        return results.map { it.toDocChunk() }
    }

    override fun searchByVersions(
        query: FloatArray,
        library: String,
        versions: List<String>,
        limit: Int,
    ): List<DocChunk> {
        val versionFilter =
            Filter
                .newBuilder()
                .addAllShould(versions.map { matchKeyword("version", it) })
                .build()

        val combinedFilter =
            Filter
                .newBuilder()
                .addMust(matchKeyword("library", library))
                .addMust(filter(versionFilter))
                .build()

        val results =
            client
                .searchAsync(
                    SearchPoints
                        .newBuilder()
                        .setCollectionName(properties.collectionName)
                        .addAllVector(query.toList())
                        .setFilter(combinedFilter)
                        .setLimit(limit.toLong())
                        .setWithPayload(enable(true))
                        .build(),
                ).get()

        return results.map { it.toDocChunk() }
    }

    override fun deleteByLibraryAndVersion(
        library: String,
        version: String,
    ) {
        val f =
            Filter
                .newBuilder()
                .addMust(matchKeyword("library", library))
                .addMust(matchKeyword("version", version))
                .build()

        client.deleteAsync(properties.collectionName, f).get()
        log.info("Deleted chunks for $library:$version")
    }

    override fun updateLatestFlag(
        library: String,
        version: String,
        latest: Boolean,
    ) {
        val f =
            Filter
                .newBuilder()
                .addMust(matchKeyword("library", library))
                .addMust(matchKeyword("version", version))
                .build()

        client
            .setPayloadAsync(
                properties.collectionName,
                mapOf("latest" to value(latest)),
                f,
                true,
                null,
                null,
            ).get()
    }

    override fun listIndexedLibraries(): List<LibraryIndexInfo> {
        val infoMap = mutableMapOf<String, MutableMap<String, LibraryIndexInfo>>()

        var nextOffset: PointId? = null
        do {
            val scrollBuilder =
                ScrollPoints
                    .newBuilder()
                    .setCollectionName(properties.collectionName)
                    .setLimit(100)
                    .setWithPayload(enable(true))

            if (nextOffset != null) {
                scrollBuilder.offset = nextOffset
            }

            val result = client.scrollAsync(scrollBuilder.build()).get()

            for (point in result.resultList) {
                val payload = point.payloadMap
                val lib = payload["library"]?.stringValue ?: continue
                val ver = payload["version"]?.stringValue ?: continue
                val isLatest = payload["latest"]?.boolValue ?: false

                val libMap = infoMap.getOrPut(lib) { mutableMapOf() }
                val existing = libMap[ver]
                libMap[ver] =
                    LibraryIndexInfo(
                        library = lib,
                        version = ver,
                        chunkCount = (existing?.chunkCount ?: 0) + 1,
                        latest = isLatest,
                    )
            }

            nextOffset = if (result.hasNextPageOffset()) result.nextPageOffset else null
        } while (nextOffset != null)

        return infoMap.values.flatMap { it.values }
    }

    private fun DocMetadata.toPayload(text: String): Map<String, io.qdrant.client.grpc.JsonWithInt.Value> = mapOf(
        "library" to value(library),
        "version" to value(version),
        "doc_type" to value(docType.name.lowercase()),
        "section" to value(section),
        "section_path" to value(sectionPath),
        "source_url" to value(sourceUrl),
        "indexed_at" to value(indexedAt.toString()),
        "latest" to value(latest),
        "text" to value(text),
    )

    private fun ScoredPoint.toDocChunk(): DocChunk = DocChunk(
        id = UUID.fromString(id.uuid),
        text = payloadMap["text"]?.stringValue ?: "",
        metadata =
        DocMetadata(
            library = payloadMap["library"]?.stringValue ?: "",
            version = payloadMap["version"]?.stringValue ?: "",
            docType =
            runCatching { DocType.valueOf((payloadMap["doc_type"]?.stringValue ?: "reference").uppercase()) }
                .getOrDefault(DocType.REFERENCE),
            section = payloadMap["section"]?.stringValue ?: "",
            sectionPath = payloadMap["section_path"]?.stringValue ?: "",
            sourceUrl = payloadMap["source_url"]?.stringValue ?: "",
            indexedAt =
            runCatching { LocalDate.parse(payloadMap["indexed_at"]?.stringValue ?: "") }
                .getOrDefault(LocalDate.now()),
            latest = payloadMap["latest"]?.boolValue ?: false,
        ),
    )
}
