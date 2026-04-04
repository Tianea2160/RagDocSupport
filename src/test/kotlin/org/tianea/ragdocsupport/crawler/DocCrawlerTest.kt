package org.tianea.ragdocsupport.crawler

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DocCrawlerTest {
    private val crawler = DocCrawler()

    @Test
    fun `crawl returns null for invalid URL`() {
        val result = crawler.crawl("https://this-domain-does-not-exist-12345.invalid")

        result.shouldBeNull()
    }

    @Test
    fun `crawlWithFallback returns empty result when all URLs fail`() {
        val result = crawler.crawlWithFallback(
            listOf(
                "https://invalid-1.invalid",
                "https://invalid-2.invalid",
            ),
        )

        result.document.shouldBeNull()
        result.resolvedUrl.shouldBeNull()
        result.failedUrls shouldHaveSize 2
    }

    @Test
    fun `crawlWithFallback collects all failed URLs`() {
        val result = crawler.crawlWithFallback(
            listOf(
                "https://invalid-1.invalid",
                "https://invalid-2.invalid",
                "https://invalid-3.invalid",
            ),
        )

        result.document.shouldBeNull()
        result.failedUrls shouldHaveSize 3
        result.failedUrls[0] shouldBe "https://invalid-1.invalid"
        result.failedUrls[2] shouldBe "https://invalid-3.invalid"
    }
}
