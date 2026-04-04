package org.tianea.ragdocsupport.crawler

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class DocCrawler {
    private val log = LoggerFactory.getLogger(javaClass)

    fun crawl(url: String): Document? = try {
        log.info("Crawling: $url")
        val document =
            Jsoup
                .connect(url)
                .timeout(CrawlerConstants.TIMEOUT_MS)
                .followRedirects(true)
                .get()

        log.info("Resolved URL: ${document.location()}")
        document
    } catch (e: Exception) {
        log.error("Failed to crawl $url: ${e.message}")
        null
    }

    fun crawlWithFallback(urls: List<String>): CrawlResult {
        val failedUrls = mutableListOf<String>()

        for (url in urls) {
            val document = crawl(url)
            if (document != null) {
                return CrawlResult(
                    document = document,
                    resolvedUrl = document.location(),
                    failedUrls = failedUrls,
                )
            }
            failedUrls.add(url)
        }

        return CrawlResult(document = null, resolvedUrl = null, failedUrls = failedUrls)
    }
}

data class CrawlResult(
    val document: Document?,
    val resolvedUrl: String?,
    val failedUrls: List<String>,
)
