package org.tianea.ragdocsupport.mcp

import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component
import org.tianea.ragdocsupport.core.port.VectorStore

@Component
class DocsListTool(
    private val vectorStore: VectorStore,
) {
    @Tool(
        description = """List all indexed documentation libraries and their versions.
Shows library name, version, number of indexed chunks, and whether it's marked as the latest version.
Use this to check what documentation is available before searching.""",
    )
    fun docsList(): String {
        val libraries = vectorStore.listIndexedLibraries()

        if (libraries.isEmpty()) {
            return "No documentation has been indexed yet. Use docs-register to add documentation."
        }

        val grouped = libraries.groupBy { it.library }

        val sb = StringBuilder()
        sb.appendLine("## Indexed Documentation")
        sb.appendLine()
        sb.appendLine("| Library | Version | Chunks | Latest |")
        sb.appendLine("|---------|---------|--------|--------|")

        for ((lib, versions) in grouped) {
            for (info in versions.sortedByDescending { it.version }) {
                val latestMark = if (info.latest) "Yes" else ""
                sb.appendLine("| $lib | ${info.version} | ${info.chunkCount} | $latestMark |")
            }
        }

        return sb.toString()
    }
}
