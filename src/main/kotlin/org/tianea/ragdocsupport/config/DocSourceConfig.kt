package org.tianea.ragdocsupport.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.tianea.ragdocsupport.core.model.DocSource
import org.tianea.ragdocsupport.core.model.DocType
import org.tianea.ragdocsupport.core.model.DocUrlPattern
import org.tianea.ragdocsupport.core.port.DocSourceRepository

@Configuration
class DocSourceConfig {
    @Bean
    fun docSourceRepository(): DocSourceRepository {
        val mapper = ObjectMapper(YAMLFactory())
        val tree = mapper.readTree(ClassPathResource("doc-sources.yml").inputStream)
        val sourcesNode = tree["sources"] ?: return EmptyDocSourceRepository

        val sources =
            sourcesNode
                .properties()
                .asSequence()
                .map { (name, node) ->
                    DocSource(
                        library = name,
                        group = node["group"].asText(),
                        artifact = node["artifact"]?.asText(),
                        docs =
                        node["docs"]
                            ?.properties()
                            ?.asSequence()
                            ?.mapNotNull { (docTypeName, docNode) ->
                                val docType =
                                    runCatching { DocType.valueOf(docTypeName.uppercase()) }.getOrNull()
                                        ?: return@mapNotNull null
                                docType to DocUrlPattern(docNode["url"].asText())
                            }?.toMap() ?: emptyMap(),
                    )
                }.toList()

        return InMemoryDocSourceRepository(sources)
    }
}

private class InMemoryDocSourceRepository(
    private val sources: List<DocSource>,
) : DocSourceRepository {
    override fun findByLibrary(library: String): DocSource? = sources.find { it.library == library }

    override fun findAll(): List<DocSource> = sources
}

private object EmptyDocSourceRepository : DocSourceRepository {
    override fun findByLibrary(library: String): DocSource? = null

    override fun findAll(): List<DocSource> = emptyList()
}
