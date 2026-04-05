package org.tianea.ragdocsupport.crawler

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.tianea.ragdocsupport.config.CrawlerProperties
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.StructuredTaskScope
import java.util.concurrent.StructuredTaskScope.Joiner
import kotlin.time.measureTimedValue

@Component
class DocTreeCrawler(
    private val crawlerProperties: CrawlerProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun crawlTree(
        seedUrl: String,
        maxDepth: Int = 2,
    ): List<TreeCrawlResult> {
        log.info("Starting tree crawl from: $seedUrl (maxDepth=$maxDepth)")

        val crawlStart = System.currentTimeMillis()
        val scopePrefix = extractScopePrefix(seedUrl)
        val visited = ConcurrentHashMap.newKeySet<String>()
        val results = ConcurrentLinkedQueue<TreeCrawlResult>()

        visited.add(normalizeUrl(seedUrl))
        var currentLevel = listOf(seedUrl)

        for (depth in 0..maxDepth) {
            if (currentLevel.isEmpty()) break

            val levelStart = System.currentTimeMillis()
            val levelSize = currentLevel.size
            val nextLevel = ConcurrentLinkedQueue<String>()

            StructuredTaskScope.open(Joiner.awaitAll<Unit>()).use { scope ->
                for (url in currentLevel) {
                    scope.fork<Unit>(
                        Runnable {
                            crawlPage(url, scopePrefix, depth, maxDepth, visited, results, nextLevel)
                        },
                    )
                }
                scope.join()
            }

            val levelElapsed = System.currentTimeMillis() - levelStart
            log.info(
                "Depth $depth complete: $levelSize URLs fetched in ${levelElapsed}ms " +
                    "(${nextLevel.size} links discovered, ${results.size} pages total)",
            )

            currentLevel = nextLevel.toList()
        }

        val totalElapsed = System.currentTimeMillis() - crawlStart
        val avgPerPage = if (results.isNotEmpty()) totalElapsed / results.size else 0
        log.info(
            "Tree crawl complete: ${results.size} pages from $seedUrl " +
                "in ${totalElapsed}ms (avg ${avgPerPage}ms/page)",
        )
        return results.toList()
    }

    private fun crawlPage(
        url: String,
        scopePrefix: String,
        depth: Int,
        maxDepth: Int,
        visited: MutableSet<String>,
        results: ConcurrentLinkedQueue<TreeCrawlResult>,
        nextLevel: ConcurrentLinkedQueue<String>,
    ) {
        val document = fetchWithRetry(url) ?: return
        results.add(TreeCrawlResult(document, url, depth))

        if (depth < maxDepth) {
            val links = extractLinks(document, scopePrefix)
            for (link in links) {
                if (visited.add(normalizeUrl(link))) {
                    nextLevel.add(link)
                }
            }
        }

        Thread.sleep(crawlerProperties.sleepTimeMs.toLong())
    }

    private fun fetchWithRetry(url: String): Document? {
        repeat(crawlerProperties.retryTimes + 1) { attempt ->
            val (result, duration) = measureTimedValue {
                runCatching {
                    Jsoup
                        .connect(url)
                        .timeout(crawlerProperties.timeoutMs)
                        .followRedirects(true)
                        .get()
                }
            }
            result.onSuccess { document ->
                log.info("GET $url -> ${document.location()} ($duration)")
                return document
            }
            result.onFailure { e ->
                if (attempt < crawlerProperties.retryTimes) {
                    log.warn("GET $url -> FAILED ($duration, retry ${attempt + 1}): ${e.message}")
                } else {
                    log.warn("GET $url -> FAILED ($duration, no more retries): ${e.message}")
                }
            }
        }
        return null
    }

    private fun extractLinks(
        document: Document,
        scopePrefix: String,
    ): List<String> = document
        .select("a[href]")
        .asSequence()
        .map { it.attr("abs:href") }
        .filter { it.isNotEmpty() }
        .map { normalizeUrl(it) }
        .filter { it.startsWith(scopePrefix) }
        .filter { !isNonHtmlResource(it) }
        .distinct()
        .toList()

    private fun extractScopePrefix(seedUrl: String): String {
        val uri = URI.create(seedUrl)
        val basePath = uri.path.removeSuffix("/")
        return "${uri.scheme}://${uri.host}$basePath"
    }

    private fun normalizeUrl(url: String): String {
        val stripped = url.substringBefore("#").substringBefore("?").removeSuffix("/")
        return stripped.ifEmpty { url.substringBefore("#").substringBefore("?") }
    }

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

data class TreeCrawlResult(
    val document: Document,
    val url: String,
    val depth: Int,
)
