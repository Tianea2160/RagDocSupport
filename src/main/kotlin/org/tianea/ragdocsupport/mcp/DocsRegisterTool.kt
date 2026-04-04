package org.tianea.ragdocsupport.mcp

import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component
import org.tianea.ragdocsupport.sync.DocSyncService

@Component
class DocsRegisterTool(
    private val syncService: DocSyncService,
) {
    @Tool(
        description = """Register and index documentation for a library.
Crawls the official documentation, chunks it, generates embeddings, and stores in vector DB.
If docUrl is omitted, the system resolves the URL from doc-sources.yml mapping.
Use this when you need documentation for a library that isn't indexed yet.""",
    )
    fun docsRegister(
        @ToolParam(description = "Library name (e.g., 'spring-boot', 'kafka')") library: String,
        @ToolParam(description = "Library version (e.g., '4.0.1')") version: String,
        @ToolParam(description = "Optional: direct URL to the documentation page", required = false) docUrl: String?,
    ): String {
        val result = syncService.register(library, version, docUrl)
        return if (result.success) {
            "Successfully indexed ${result.chunksIndexed} chunks for $library:$version. ${result.message}"
        } else {
            "Failed to index $library:$version. ${result.message}"
        }
    }
}
