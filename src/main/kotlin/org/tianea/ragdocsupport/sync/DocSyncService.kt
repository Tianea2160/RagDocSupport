package org.tianea.ragdocsupport.sync

import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.stereotype.Service
import org.tianea.ragdocsupport.core.model.Dependency
import org.tianea.ragdocsupport.core.model.DocChunk
import org.tianea.ragdocsupport.core.model.DocMetadata
import org.tianea.ragdocsupport.core.model.DocType
import org.tianea.ragdocsupport.core.model.DocUrlPattern
import org.tianea.ragdocsupport.core.port.DocSourceRepository
import org.tianea.ragdocsupport.core.port.VectorStore
import org.tianea.ragdocsupport.crawler.DocChunker
import org.tianea.ragdocsupport.crawler.DocCrawler
import org.tianea.ragdocsupport.crawler.DocTreeCrawler
import org.tianea.ragdocsupport.crawler.HtmlToMarkdownConverter

@Service
class DocSyncService(
    private val crawler: DocCrawler,
    private val treeCrawler: DocTreeCrawler,
    private val converter: HtmlToMarkdownConverter,
    private val chunker: DocChunker,
    private val embeddingModel: EmbeddingModel,
    private val vectorStore: VectorStore,
    private val docSourceRepository: DocSourceRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun register(
        library: String,
        version: String,
        docUrl: String? = null,
    ): RegisterResult {
        val dependency = Dependency(group = "", artifact = "", version = version)

        val urlCandidates = resolveUrlCandidates(library, dependency, docUrl)
        if (urlCandidates.isEmpty()) {
            return RegisterResult(success = false)
        }

        vectorStore.ensureCollection()

        val existingVersions =
            vectorStore
                .listIndexedLibraries()
                .filter { it.library == library && it.latest }
        for (info in existingVersions) {
            vectorStore.updateLatestFlag(library, info.version, false)
        }

        vectorStore.deleteByLibraryAndVersion(library, version)

        var totalChunks = 0
        val failedDocTypes = mutableListOf<FailedDocType>()

        for ((docType, pattern, candidateUrls) in urlCandidates) {
            log.info("Processing $library:$version ($docType) with ${candidateUrls.size} candidate URL(s)")

            val chunks =
                if (pattern.recursive) {
                    processRecursive(library, version, docType, candidateUrls, pattern.maxDepth)
                } else {
                    processSingle(library, version, docType, candidateUrls)
                }

            if (chunks == null) {
                failedDocTypes.add(FailedDocType(docType, candidateUrls))
                continue
            }

            vectorStore.upsert(chunks)
            totalChunks += chunks.size
        }

        return RegisterResult(
            success = totalChunks > 0,
            chunksIndexed = totalChunks,
            failedDocTypes = failedDocTypes,
        )
    }

    private fun processSingle(
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

        val embeddings = embeddingModel.embedAll(chunks.map { it.text })
        return chunks.mapIndexed { idx, chunk -> chunk.copy(embedding = embeddings[idx]) }
    }

    private fun processRecursive(
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

            val allTexts = preEmbedChunks.map { it.text }
            val allEmbeddings = embeddingModel.embedAll(allTexts)
            log.info("Batch-embedded ${allTexts.size} chunks for $library:$version ($docType)")

            return preEmbedChunks.mapIndexed { idx, chunk ->
                chunk.copy(embedding = allEmbeddings[idx])
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

    private fun resolveUrlCandidates(
        library: String,
        dependency: Dependency,
        explicitUrl: String?,
    ): List<DocTypeCandidates> {
        if (explicitUrl != null) {
            return listOf(
                DocTypeCandidates(DocType.REFERENCE, DocUrlPattern(explicitUrl), listOf(explicitUrl)),
            )
        }

        val source = docSourceRepository.findByLibrary(library) ?: return emptyList()
        return source.docs.map { (docType, pattern) ->
            DocTypeCandidates(docType, pattern, pattern.resolveAll(dependency))
        }
    }

    private fun EmbeddingModel.embedAll(texts: List<String>): List<FloatArray> = embed(texts)
}

data class DocTypeCandidates(
    val docType: DocType,
    val pattern: DocUrlPattern,
    val urls: List<String>,
)

data class RegisterResult(
    val success: Boolean,
    val chunksIndexed: Int = 0,
    val failedDocTypes: List<FailedDocType> = emptyList(),
)

data class FailedDocType(
    val docType: DocType,
    val triedUrls: List<String>,
)
