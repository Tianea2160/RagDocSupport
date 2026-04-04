package org.tianea.ragdocsupport.config

import io.qdrant.client.QdrantClient
import io.qdrant.client.QdrantGrpcClient
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(QdrantProperties::class)
class AppConfig {
    @Bean
    fun qdrantClient(properties: QdrantProperties): QdrantClient = QdrantClient(
        QdrantGrpcClient.newBuilder(properties.host, properties.port, false).build(),
    )
}
