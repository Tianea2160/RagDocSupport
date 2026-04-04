package org.tianea.ragdocsupport.sync

import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.stereotype.Component
import org.tianea.ragdocsupport.core.model.DocChunk
import org.tianea.ragdocsupport.core.model.DocMetadata
import org.tianea.ragdocsupport.core.model.DocType
import org.tianea.ragdocsupport.crawler.DocChunker
import org.tianea.ragdocsupport.crawler.DocCrawler
import org.tianea.ragdocsupport.crawler.DocTreeCrawler
import org.tianea.ragdocsupport.crawler.HtmlToMarkdownConverter

@Component
class DocProcessor(
    private val crawler: DocCrawler,
    private val treeCrawler: DocTreeCrawler,
    private val converter: HtmlToMarkdownConverter,
    private val chunker: DocChunker,
    private val embeddingModel: EmbeddingModel,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun processSingle(
        library: String,
        version: String,
        docType: DocType,
        candidateUrls: List<String>,
    ): List<DocChunk>? {
        val crawlResult = crawler.crawlWithFallback(candidateUrls)
        val document = crawlResult.document
        val resolvedUrl = crawlResult.resolvedUrl
        if (document == null || resolvedUrl == null) {
            log.warn("All candidate URLs failed for $library:$version ($docType)")
            return null
        }

        log.info("Successfully crawled $library:$version ($docType) from $resolvedUrl")
        val chunks = convertAndChunk(document, resolvedUrl, library, version, docType)
        if (chunks.isEmpty()) return null

        return embedChunks(chunks)
    }

    fun processRecursive(
        library: String,
        version: String,
        docType: DocType,
        candidateUrls: List<String>,
        maxDepth: Int,
    ): List<DocChunk>? {
        for (seedUrl in candidateUrls) {
            val treeResults = treeCrawler.crawlTree(seedUrl, maxDepth)
            if (treeResults.isEmpty()) {
                log.warn("Tree crawl returned no results for $seedUrl")
                continue
            }

            log.info(
                "Tree crawl for $library:$version ($docType) returned ${treeResults.size} pages from $seedUrl",
            )

            val preEmbedChunks = treeResults.flatMap { result ->
                convertAndChunk(result.document, result.url, library, version, docType)
            }

            if (preEmbedChunks.isEmpty()) continue

            return embedChunks(preEmbedChunks).also {
                log.info("Batch-embedded ${it.size} chunks for $library:$version ($docType)")
            }
        }

        log.warn("All candidate URLs failed for recursive crawl $library:$version ($docType)")
        return null
    }

    private fun convertAndChunk(
        document: Document,
        sourceUrl: String,
        library: String,
        version: String,
        docType: DocType,
    ): List<DocChunk> {
        val markdown = converter.convert(document)
        if (markdown.isBlank()) {
            log.debug("Empty content from: $sourceUrl")
            return emptyList()
        }

        val chunkResults = chunker.chunk(markdown)
        log.debug("Created ${chunkResults.size} chunks from $sourceUrl")

        return chunkResults.map { chunkResult ->
            DocChunk(
                text = chunkResult.text,
                metadata = DocMetadata(
                    library = library,
                    version = version,
                    docType = docType,
                    section = chunkResult.section,
                    sectionPath = chunkResult.sectionPath,
                    sourceUrl = sourceUrl,
                ),
            )
        }
    }

    private fun embedChunks(chunks: List<DocChunk>): List<DocChunk> {
        val embeddings = embeddingModel.embed(chunks.map { it.text })
        return chunks.mapIndexed { idx, chunk -> chunk.copy(embedding = embeddings[idx]) }
    }
}
