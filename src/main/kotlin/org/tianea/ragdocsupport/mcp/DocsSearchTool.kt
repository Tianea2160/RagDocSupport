package org.tianea.ragdocsupport.mcp

import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component
import org.tianea.ragdocsupport.core.port.VectorStore

@Component
class DocsSearchTool(
    private val vectorStore: VectorStore,
    private val embeddingModel: EmbeddingModel,
) {
    @McpTool(
        description = """Search indexed documentation using semantic search.
Returns relevant documentation chunks matching the query.
Optionally filter by library name and/or version.""",
    )
    fun docsSearch(
        @McpToolParam(description = "Natural language search query (e.g., 'kafka consumer max.poll.interval.ms default value')") query: String,
        @McpToolParam(description = "Optional: filter by library name", required = false) library: String?,
        @McpToolParam(description = "Optional: filter by version", required = false) version: String?,
        @McpToolParam(description = "Number of results to return (default: 5)", required = false) limit: Int?,
    ): String {
        val queryEmbedding = embedQuery(query)
        val results = vectorStore.search(queryEmbedding, library, version, limit ?: 5)

        if (results.isEmpty()) {
            return "No results found for query: '$query'" +
                (library?.let { " (library: $it)" } ?: "") +
                (version?.let { " (version: $it)" } ?: "")
        }

        return results
            .mapIndexed { idx, chunk ->
                """
            |--- Result ${idx + 1} ---
            |Library: ${chunk.metadata.library} v${chunk.metadata.version}
            |Section: ${chunk.metadata.sectionPath}
            |Source: ${chunk.metadata.sourceUrl}
            |
            |${chunk.text}
                """.trimMargin()
            }.joinToString("\n\n")
    }

    private fun embedQuery(text: String): FloatArray = embeddingModel.embed(text)
}
