package org.tianea.ragdocsupport.store

import io.qdrant.client.ConditionFactory.filter
import io.qdrant.client.ConditionFactory.matchKeyword
import io.qdrant.client.grpc.Common.Filter
import org.tianea.ragdocsupport.store.DocChunkPayloadMapper.FIELD_LIBRARY
import org.tianea.ragdocsupport.store.DocChunkPayloadMapper.FIELD_VERSION

object QdrantFilterBuilder {
    fun libraryVersionFilter(
        library: String,
        version: String,
    ): Filter = Filter
        .newBuilder()
        .addMust(matchKeyword(FIELD_LIBRARY, library))
        .addMust(matchKeyword(FIELD_VERSION, version))
        .build()

    fun searchFilter(
        library: String?,
        version: String?,
    ): Filter {
        val builder = Filter.newBuilder()
        if (library != null) {
            builder.addMust(matchKeyword(FIELD_LIBRARY, library))
        }
        if (version != null) {
            builder.addMust(matchKeyword(FIELD_VERSION, version))
        }
        return builder.build()
    }

    fun multiVersionFilter(
        library: String,
        versions: List<String>,
    ): Filter {
        val versionFilter =
            Filter
                .newBuilder()
                .addAllShould(versions.map { matchKeyword(FIELD_VERSION, it) })
                .build()

        return Filter
            .newBuilder()
            .addMust(matchKeyword(FIELD_LIBRARY, library))
            .addMust(filter(versionFilter))
            .build()
    }
}
