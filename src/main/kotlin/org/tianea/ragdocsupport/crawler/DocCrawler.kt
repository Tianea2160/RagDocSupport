package org.tianea.ragdocsupport.crawler

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class DocCrawler(
    private val restClient: RestClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun crawl(url: String): Document? {
        return try {
            log.info("Crawling: $url")
            val html =
                restClient
                    .get()
                    .uri(url)
                    .retrieve()
                    .body(String::class.java)
                    ?: return null

            Jsoup.parse(html, url)
        } catch (e: Exception) {
            log.error("Failed to crawl $url: ${e.message}")
            null
        }
    }
}
