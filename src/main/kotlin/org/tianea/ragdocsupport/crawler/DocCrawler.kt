package org.tianea.ragdocsupport.crawler

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.tianea.ragdocsupport.config.CrawlerProperties
import kotlin.time.measureTimedValue

@Component
class DocCrawler(
    private val crawlerProperties: CrawlerProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun crawl(url: String): Document? {
        val (result, duration) = measureTimedValue {
            runCatching {
                Jsoup
                    .connect(url)
                    .timeout(crawlerProperties.timeoutMs)
                    .followRedirects(true)
                    .get()
            }
        }
        return result
            .onSuccess { log.info("GET $url -> ${it.location()} ($duration)") }
            .onFailure { log.warn("GET $url -> FAILED ($duration): ${it.message}") }
            .getOrNull()
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
