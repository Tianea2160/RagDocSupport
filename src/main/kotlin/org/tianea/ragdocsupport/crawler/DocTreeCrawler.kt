package org.tianea.ragdocsupport.crawler

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.tianea.ragdocsupport.config.CrawlerProperties
import java.net.URI
import kotlin.time.measureTime
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

        val scopePrefix = extractScopePrefix(seedUrl)
        val visited = mutableSetOf(normalizeUrl(seedUrl))
        val results = mutableListOf<TreeCrawlResult>()

        var currentLevel = listOf(seedUrl)

        val totalDuration = measureTime {
            for (depth in 0..maxDepth) {
                if (currentLevel.isEmpty()) break

                val levelSize = currentLevel.size
                val nextLevel = mutableListOf<String>()

                val levelDuration = measureTime {
                    for (url in currentLevel) {
                        crawlPage(url, scopePrefix, depth, maxDepth, visited, results, nextLevel)
                    }
                }
                log.info(
                    "Depth $depth complete: $levelSize URLs fetched in $levelDuration " +
                        "(${nextLevel.size} links discovered, ${results.size} pages total)",
                )

                currentLevel = nextLevel
            }
        }

        val avgPerPage = if (results.isNotEmpty()) totalDuration / results.size else totalDuration
        log.info(
            "Tree crawl complete: ${results.size} pages from $seedUrl " +
                "in $totalDuration (avg $avgPerPage/page)",
        )
        return results
    }

    private fun crawlPage(
        url: String,
        scopePrefix: String,
        depth: Int,
        maxDepth: Int,
        visited: MutableSet<String>,
        results: MutableList<TreeCrawlResult>,
        nextLevel: MutableList<String>,
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
