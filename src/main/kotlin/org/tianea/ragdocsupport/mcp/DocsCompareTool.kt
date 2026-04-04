package org.tianea.ragdocsupport.mcp

import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component
import org.tianea.ragdocsupport.core.port.VectorStore

@Component
class DocsCompareTool(
    private val vectorStore: VectorStore,
    private val embeddingModel: EmbeddingModel,
) {
    @McpTool(
        description = """Compare documentation between two versions of a library.
Searches migration guides, changelogs, and reference docs for both versions.
Useful for understanding breaking changes, new features, and migration steps.""",
    )
    fun docsCompare(
        @McpToolParam(description = "Library name (e.g., 'spring-boot')") library: String,
        @McpToolParam(description = "Old version (e.g., '3.4.0')") fromVersion: String,
        @McpToolParam(description = "New version (e.g., '4.0.1')") toVersion: String,
        @McpToolParam(description = "Search query for specific topic (e.g., 'security configuration changes')", required = false) query: String?,
    ): String {
        val searchQuery = query ?: "migration changes from $fromVersion to $toVersion"
        val queryEmbedding = embedQuery(searchQuery)

        val results =
            vectorStore.searchByVersions(
                query = queryEmbedding,
                library = library,
                versions = listOf(fromVersion, toVersion),
                limit = 10,
            )

        if (results.isEmpty()) {
            return "No comparison results found for $library ($fromVersion → $toVersion). " +
                "Make sure both versions are indexed using docs-register."
        }

        val fromResults = results.filter { it.metadata.version == fromVersion }
        val toResults = results.filter { it.metadata.version == toVersion }

        val sb = StringBuilder()
        sb.appendLine("## Comparison: $library $fromVersion → $toVersion")
        sb.appendLine()

        if (toResults.isNotEmpty()) {
            sb.appendLine("### Version $toVersion")
            for (chunk in toResults) {
                sb.appendLine("- [${chunk.metadata.docType}] ${chunk.metadata.section}")
                sb.appendLine("  ${chunk.text.take(300)}...")
                sb.appendLine()
            }
        }

        if (fromResults.isNotEmpty()) {
            sb.appendLine("### Version $fromVersion")
            for (chunk in fromResults) {
                sb.appendLine("- [${chunk.metadata.docType}] ${chunk.metadata.section}")
                sb.appendLine("  ${chunk.text.take(300)}...")
                sb.appendLine()
            }
        }

        return sb.toString()
    }

    private fun embedQuery(text: String): FloatArray = embeddingModel.embed(text)
}
