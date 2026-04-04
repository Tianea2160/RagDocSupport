package org.tianea.ragdocsupport.crawler

import org.springframework.stereotype.Component

@Component
class DocChunker {
    companion object {
        private const val MAX_CHUNK_SIZE = 1000 // characters
        private const val OVERLAP_SIZE = 120
    }

    data class ChunkResult(
        val text: String,
        val section: String,
        val sectionPath: String,
    )

    fun chunk(markdown: String): List<ChunkResult> {
        val sections = splitBySections(markdown)
        val chunks = mutableListOf<ChunkResult>()

        for (section in sections) {
            if (section.text.length <= MAX_CHUNK_SIZE) {
                chunks.add(section)
            } else {
                // Try splitting by sub-headers (###)
                val subSections = splitBySubSections(section)
                if (subSections.size > 1) {
                    for (sub in subSections) {
                        if (sub.text.length <= MAX_CHUNK_SIZE) {
                            chunks.add(sub)
                        } else {
                            chunks.addAll(fixedSizeChunk(sub))
                        }
                    }
                } else {
                    chunks.addAll(fixedSizeChunk(section))
                }
            }
        }

        return chunks.filter { it.text.isNotBlank() }
    }

    private fun splitBySections(markdown: String): List<ChunkResult> {
        val sections = mutableListOf<ChunkResult>()
        val lines = markdown.lines()
        val currentLines = mutableListOf<String>()
        var currentHeader = ""
        val pathStack = mutableListOf<String>()

        for (line in lines) {
            when {
                line.startsWith("## ") -> {
                    if (currentLines.isNotEmpty()) {
                        sections.add(
                            ChunkResult(
                                text = currentLines.joinToString("\n").trim(),
                                section = currentHeader,
                                sectionPath = pathStack.joinToString(" > "),
                            ),
                        )
                        currentLines.clear()
                    }
                    currentHeader = line.removePrefix("## ").trim()
                    pathStack.clear()
                    pathStack.add(currentHeader)
                    currentLines.add(line)
                }

                line.startsWith("# ") -> {
                    if (currentLines.isNotEmpty()) {
                        sections.add(
                            ChunkResult(
                                text = currentLines.joinToString("\n").trim(),
                                section = currentHeader,
                                sectionPath = pathStack.joinToString(" > "),
                            ),
                        )
                        currentLines.clear()
                    }
                    currentHeader = line.removePrefix("# ").trim()
                    pathStack.clear()
                    pathStack.add(currentHeader)
                    currentLines.add(line)
                }

                else -> currentLines.add(line)
            }
        }

        if (currentLines.isNotEmpty()) {
            sections.add(
                ChunkResult(
                    text = currentLines.joinToString("\n").trim(),
                    section = currentHeader,
                    sectionPath = pathStack.joinToString(" > "),
                ),
            )
        }

        return sections
    }

    private fun splitBySubSections(section: ChunkResult): List<ChunkResult> {
        val subSections = mutableListOf<ChunkResult>()
        val lines = section.text.lines()
        val currentLines = mutableListOf<String>()
        var currentSubHeader: String? = null

        for (line in lines) {
            if (line.startsWith("### ")) {
                if (currentLines.isNotEmpty()) {
                    val subSection = currentSubHeader ?: section.section
                    val path =
                        if (currentSubHeader != null) {
                            "${section.sectionPath} > $currentSubHeader"
                        } else {
                            section.sectionPath
                        }
                    subSections.add(
                        ChunkResult(
                            text = currentLines.joinToString("\n").trim(),
                            section = subSection,
                            sectionPath = path,
                        ),
                    )
                    currentLines.clear()
                }
                currentSubHeader = line.removePrefix("### ").trim()
            }
            currentLines.add(line)
        }

        if (currentLines.isNotEmpty()) {
            val subSection = currentSubHeader ?: section.section
            val path =
                if (currentSubHeader != null) {
                    "${section.sectionPath} > $currentSubHeader"
                } else {
                    section.sectionPath
                }
            subSections.add(
                ChunkResult(
                    text = currentLines.joinToString("\n").trim(),
                    section = subSection,
                    sectionPath = path,
                ),
            )
        }

        return subSections
    }

    private fun fixedSizeChunk(section: ChunkResult): List<ChunkResult> {
        val text = section.text
        val chunks = mutableListOf<ChunkResult>()
        var start = 0

        while (start < text.length) {
            val end = (start + MAX_CHUNK_SIZE).coerceAtMost(text.length)
            chunks.add(section.copy(text = text.substring(start, end)))
            start = (end - OVERLAP_SIZE).coerceAtLeast(start + 1)
            if (end == text.length) break
        }

        return chunks
    }
}
