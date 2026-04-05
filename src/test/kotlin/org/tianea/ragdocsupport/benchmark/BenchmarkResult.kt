package org.tianea.ragdocsupport.benchmark

import org.tianea.ragdocsupport.core.model.DocChunk

data class BenchmarkResult(
    val query: BenchmarkQuery,
    val chunks: List<DocChunk>,
    val searchTimeMs: Long,
) {
    val hasResults: Boolean get() = chunks.isNotEmpty()
}

data class BenchmarkReport(
    val results: List<BenchmarkResult>,
) {
    val totalQueries: Int get() = results.size
    val queriesWithResults: Int get() = results.count { it.hasResults }
    val hitRate: Double get() = if (totalQueries == 0) 0.0 else queriesWithResults.toDouble() / totalQueries
    val avgSearchTimeMs: Long get() = if (results.isEmpty()) 0 else results.map { it.searchTimeMs }.average().toLong()

    fun formatReport(): String = buildString {
        appendLine("=".repeat(80))
        appendLine("RAG BENCHMARK REPORT")
        appendLine("=".repeat(80))
        appendLine()
        appendLine("## Summary")
        appendLine("  Total queries:        $totalQueries")
        appendLine("  Queries with results: $queriesWithResults")
        appendLine("  Hit rate:             ${"%.1f".format(hitRate * 100)}%")
        appendLine("  Avg search time:      ${avgSearchTimeMs}ms")
        appendLine()

        for ((idx, result) in results.withIndex()) {
            appendLine("-".repeat(80))
            appendLine("Query ${idx + 1}: ${result.query.query}")
            appendLine("  Library: ${result.query.library} | Category: ${result.query.category} | Difficulty: ${result.query.difficulty}")
            appendLine("  Search time: ${result.searchTimeMs}ms | Results: ${result.chunks.size}")
            appendLine()

            if (result.chunks.isEmpty()) {
                appendLine("  [NO RESULTS]")
            } else {
                for ((chunkIdx, chunk) in result.chunks.withIndex()) {
                    appendLine("  --- Chunk ${chunkIdx + 1} ---")
                    appendLine("  Section: ${chunk.metadata.sectionPath}")
                    appendLine("  Source:  ${chunk.metadata.sourceUrl}")
                    appendLine("  Text (first 300 chars):")
                    val preview = chunk.text.take(300).lines().joinToString("\n") { "    $it" }
                    appendLine(preview)
                    appendLine()
                }
            }
        }

        appendLine("=".repeat(80))
        appendLine("END OF REPORT")
        appendLine("=".repeat(80))
    }
}
