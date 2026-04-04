package org.tianea.ragdocsupport.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "qdrant")
data class QdrantProperties(
    val host: String = "localhost",
    val port: Int = 6334,
    val collectionName: String = "doc-chunks",
)
