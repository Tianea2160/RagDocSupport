package org.tianea.ragdocsupport.crawler

import org.slf4j.LoggerFactory
import us.codecraft.webmagic.Page
import us.codecraft.webmagic.Site
import us.codecraft.webmagic.processor.PageProcessor
import java.net.URI

internal class DocPageProcessor(
    private val scopePrefix: String,
    private val maxDepth: Int,
) : PageProcessor {
    private val log = LoggerFactory.getLogger(javaClass)
    private val scopePath: String = URI.create(scopePrefix).path

    private val site: Site =
        Site
            .me()
            .setSleepTime(CrawlerConstants.SLEEP_TIME_MS)
            .setTimeOut(CrawlerConstants.TIMEOUT_MS)
            .setRetryTimes(CrawlerConstants.RETRY_TIMES)
            .setCycleRetryTimes(0)

    override fun process(page: Page) {
        val currentUrl = page.url.toString()
        val document = page.html.document

        page.putField(CrawlerConstants.FIELD_DOCUMENT, document)
        page.putField(CrawlerConstants.FIELD_URL, currentUrl)

        val depth = calculateDepth(currentUrl)
        page.putField(CrawlerConstants.FIELD_DEPTH, depth)

        if (depth < maxDepth) {
            val links =
                document
                    .select("a[href]")
                    .asSequence()
                    .map { it.attr("abs:href") }
                    .filter { it.isNotEmpty() }
                    .map { normalizeUrl(it) }
                    .filter { isInScope(it) }
                    .filter { !isNonHtmlResource(it) }
                    .distinct()
                    .toList()

            if (links.isNotEmpty()) {
                log.debug("Found ${links.size} in-scope links at depth $depth from $currentUrl")
                page.addTargetRequests(links)
            }
        }
    }

    override fun getSite(): Site = site

    private fun calculateDepth(url: String): Int {
        val urlPath = URI.create(url).path
        val relative = urlPath.removePrefix(scopePath).removePrefix("/")
        return if (relative.isEmpty()) 0 else relative.split("/").filter { it.isNotEmpty() }.size
    }

    private fun normalizeUrl(url: String): String {
        val stripped = url.substringBefore("#").substringBefore("?").removeSuffix("/")
        return stripped.ifEmpty { url.substringBefore("#").substringBefore("?") }
    }

    private fun isInScope(url: String): Boolean = url.startsWith(scopePrefix)

    private fun isNonHtmlResource(url: String): Boolean = try {
        val path = URI.create(url).path.lowercase()
        NON_HTML_EXTENSIONS.any { path.endsWith(it) }
    } catch (_: Exception) {
        true
    }

    companion object {
        private val NON_HTML_EXTENSIONS =
            listOf(
                ".pdf", ".zip", ".tar.gz", ".jar",
                ".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico",
                ".css", ".js", ".json", ".xml",
                ".woff", ".woff2", ".ttf", ".eot",
            )
    }
}
