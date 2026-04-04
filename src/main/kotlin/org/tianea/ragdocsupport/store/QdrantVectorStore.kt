package org.tianea.ragdocsupport.store

import io.qdrant.client.PointIdFactory.id
import io.qdrant.client.QdrantClient
import io.qdrant.client.ValueFactory.value
import io.qdrant.client.VectorsFactory.vectors
import io.qdrant.client.WithPayloadSelectorFactory.enable
import io.qdrant.client.grpc.Collections.Distance
import io.qdrant.client.grpc.Collections.VectorParams
import io.qdrant.client.grpc.Common.Filter
import io.qdrant.client.grpc.Common.PointId
import io.qdrant.client.grpc.Points.PayloadIncludeSelector
import io.qdrant.client.grpc.Points.PointStruct
import io.qdrant.client.grpc.Points.ScrollPoints
import io.qdrant.client.grpc.Points.SearchPoints
import io.qdrant.client.grpc.Points.WithPayloadSelector
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.tianea.ragdocsupport.config.QdrantProperties
import org.tianea.ragdocsupport.core.model.DocChunk
import org.tianea.ragdocsupport.core.port.LibraryIndexInfo
import org.tianea.ragdocsupport.core.port.VectorStore

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
                val embedding = chunk.embedding
                    ?: throw IllegalArgumentException("Embedding is required for upsert")
                PointStruct
                    .newBuilder()
                    .setId(id(chunk.id))
                    .setVectors(vectors(embedding.toList()))
                    .putAllPayload(DocChunkPayloadMapper.toPayload(chunk.metadata, chunk.text))
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
    ): List<DocChunk> = executeSearch(query, QdrantFilterBuilder.searchFilter(library, version), limit)

    override fun searchByVersions(
        query: FloatArray,
        library: String,
        versions: List<String>,
        limit: Int,
    ): List<DocChunk> = executeSearch(query, QdrantFilterBuilder.multiVersionFilter(library, versions), limit)

    private fun executeSearch(
        query: FloatArray,
        filter: Filter,
        limit: Int,
    ): List<DocChunk> {
        val results =
            client
                .searchAsync(
                    SearchPoints
                        .newBuilder()
                        .setCollectionName(properties.collectionName)
                        .addAllVector(query.toList())
                        .setFilter(filter)
                        .setLimit(limit.toLong())
                        .setWithPayload(enable(true))
                        .build(),
                ).get()

        return results.map { DocChunkPayloadMapper.fromScoredPoint(it) }
    }

    override fun deleteByLibraryAndVersion(
        library: String,
        version: String,
    ) {
        client
            .deleteAsync(
                properties.collectionName,
                QdrantFilterBuilder.libraryVersionFilter(library, version),
            ).get()
        log.info("Deleted chunks for $library:$version")
    }

    override fun updateLatestFlag(
        library: String,
        version: String,
        latest: Boolean,
    ) {
        client
            .setPayloadAsync(
                properties.collectionName,
                mapOf(DocChunkPayloadMapper.FIELD_LATEST to value(latest)),
                QdrantFilterBuilder.libraryVersionFilter(library, version),
                true,
                null,
                null,
            ).get()
    }

    override fun listIndexedLibraries(): List<LibraryIndexInfo> {
        val infoMap = mutableMapOf<String, MutableMap<String, LibraryIndexInfo>>()

        val payloadSelector = WithPayloadSelector.newBuilder()
            .setInclude(
                PayloadIncludeSelector.newBuilder()
                    .addAllFields(
                        listOf(
                            DocChunkPayloadMapper.FIELD_LIBRARY,
                            DocChunkPayloadMapper.FIELD_VERSION,
                            DocChunkPayloadMapper.FIELD_LATEST,
                        ),
                    ),
            ).build()

        var nextOffset: PointId? = null
        do {
            val scrollBuilder =
                ScrollPoints
                    .newBuilder()
                    .setCollectionName(properties.collectionName)
                    .setLimit(100)
                    .setWithPayload(payloadSelector)

            if (nextOffset != null) {
                scrollBuilder.offset = nextOffset
            }

            val result = client.scrollAsync(scrollBuilder.build()).get()

            for (point in result.resultList) {
                val payload = point.payloadMap
                val lib = payload[DocChunkPayloadMapper.FIELD_LIBRARY]?.stringValue ?: continue
                val ver = payload[DocChunkPayloadMapper.FIELD_VERSION]?.stringValue ?: continue
                val isLatest = payload[DocChunkPayloadMapper.FIELD_LATEST]?.boolValue ?: false

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
}
