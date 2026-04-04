package org.tianea.ragdocsupport.mcp

import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component
import org.tianea.ragdocsupport.sync.DocSyncService

@Component
class DocsRegisterTool(
    private val syncService: DocSyncService,
) {
    @McpTool(
        description = """Register and index documentation for a library.
Crawls the official documentation, chunks it, generates embeddings, and stores in vector DB.
If docUrl is omitted, the system resolves the URL from doc-sources.yml mapping.
Use this when you need documentation for a library that isn't indexed yet.""",
    )
    fun docsRegister(
        @McpToolParam(description = "Library name (e.g., 'spring-boot', 'kafka')") library: String,
        @McpToolParam(description = "Library version (e.g., '4.0.1')") version: String,
        @McpToolParam(description = "Optional: direct URL to the documentation page", required = false) docUrl: String?,
    ): String {
        val result = syncService.register(library, version, docUrl)
        return buildString {
            if (result.success) {
                append("Successfully indexed ${result.chunksIndexed} chunks for $library:$version.")
            } else {
                append("Failed to index $library:$version.")
            }
            if (result.failedDocTypes.isNotEmpty()) {
                appendLine()
                appendLine("Failed doc types:")
                for (failed in result.failedDocTypes) {
                    appendLine("  - ${failed.docType}: tried ${failed.triedUrls.joinToString(", ")}")
                }
                append("Tip: You can retry with an explicit docUrl parameter if the URL pattern is outdated.")
            }
        }
    }
}
