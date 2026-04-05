package org.tianea.ragdocsupport.crawler

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.tianea.ragdocsupport.config.CrawlerProperties
import org.tianea.ragdocsupport.sync.ProgressEvent
import org.tianea.ragdocsupport.sync.ProgressEventType
import org.tianea.ragdocsupport.sync.ProgressListener
import kotlin.time.measureTimedValue

@Component
class DocCrawler(
    private val crawlerProperties: CrawlerProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun crawl(
        url: String,
        listener: ProgressListener = ProgressListener.NOOP,
    ): Document? {
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
            .onSuccess {
                val msg = "GET $url -> ${it.location()} ($duration)"
                log.info(msg)
                listener.onEvent(ProgressEvent(ProgressEventType.CRAWL, msg))
            }
            .onFailure {
                val msg = "GET $url -> FAILED ($duration): ${it.message}"
                log.warn(msg)
                listener.onEvent(ProgressEvent(ProgressEventType.WARN, msg))
            }
            .getOrNull()
    }

    fun crawlWithFallback(
        urls: List<String>,
        listener: ProgressListener = ProgressListener.NOOP,
    ): CrawlResult {
        val failedUrls = mutableListOf<String>()

        for (url in urls) {
            val document = crawl(url, listener)
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
