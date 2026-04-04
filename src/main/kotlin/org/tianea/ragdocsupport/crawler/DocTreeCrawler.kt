package org.tianea.ragdocsupport.crawler

import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import us.codecraft.webmagic.Spider
import java.net.URI

@Component
class DocTreeCrawler {
    private val log = LoggerFactory.getLogger(javaClass)

    fun crawlTree(
        seedUrl: String,
        maxDepth: Int = 2,
    ): List<TreeCrawlResult> {
        log.info("Starting tree crawl from: $seedUrl (maxDepth=$maxDepth)")

        val scopePrefix = extractScopePrefix(seedUrl)
        val results = mutableListOf<TreeCrawlResult>()

        val processor = DocPageProcessor(scopePrefix, maxDepth)
        val collector = DocResultPipeline(results)

        Spider
            .create(processor)
            .addUrl(seedUrl)
            .addPipeline(collector)
            .thread(1)
            .run()

        log.info("Tree crawl complete: ${results.size} pages from $seedUrl")
        return results.toList()
    }

    private fun extractScopePrefix(seedUrl: String): String {
        val uri = URI.create(seedUrl)
        val basePath = uri.path.removeSuffix("/")
        return "${uri.scheme}://${uri.host}$basePath"
    }
}

data class TreeCrawlResult(
    val document: Document,
    val url: String,
    val depth: Int,
)
