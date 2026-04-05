package org.tianea.ragdocsupport.mcp

import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component
import org.tianea.ragdocsupport.sync.BulkRegisterRequest
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

    @McpTool(
        description = """Register and index documentation for multiple libraries in parallel.
Each library is processed concurrently for faster bulk indexing.
Input format: 'library:version' entries separated by commas.
Example: 'spring-boot:4.0.4, jooq:3.20.11, postgresql:42.7.7'""",
    )
    fun docsRegisterBulk(
        @McpToolParam(
            description = "Comma-separated list of 'library:version' pairs (e.g., 'spring-boot:4.0.4, jooq:3.20.11')",
        ) libraries: String,
    ): String {
        val requests = libraries.split(",").map { entry ->
            val (library, version) = entry.trim().split(":")
            BulkRegisterRequest(library.trim(), version.trim())
        }

        val result = syncService.registerBulk(requests)
        return buildString {
            appendLine("Bulk registration complete: ${result.successCount}/${result.entries.size} succeeded, ${result.totalChunks} total chunks.")
            for (entry in result.entries) {
                val status = if (entry.result.success) "OK" else "FAIL"
                appendLine("  [$status] ${entry.library}:${entry.version} — ${entry.result.chunksIndexed} chunks")
                for (failed in entry.result.failedDocTypes) {
                    appendLine("    - ${failed.docType}: tried ${failed.triedUrls.joinToString(", ")}")
                }
            }
        }
    }
}
