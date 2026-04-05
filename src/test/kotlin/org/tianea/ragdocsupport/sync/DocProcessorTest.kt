package org.tianea.ragdocsupport.sync

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.jsoup.Jsoup
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.ai.embedding.EmbeddingModel
import org.tianea.ragdocsupport.core.model.DocType
import org.tianea.ragdocsupport.crawler.CrawlResult
import org.tianea.ragdocsupport.crawler.DocChunker
import org.tianea.ragdocsupport.crawler.DocCrawler
import org.tianea.ragdocsupport.crawler.DocTreeCrawler
import org.tianea.ragdocsupport.crawler.HtmlToMarkdownConverter
import org.tianea.ragdocsupport.crawler.TreeCrawlResult
import org.tianea.ragdocsupport.fixture.anEmbedding

class DocProcessorTest {
    private val crawler = mockk<DocCrawler>()
    private val treeCrawler = mockk<DocTreeCrawler>()
    private val converter = mockk<HtmlToMarkdownConverter>()
    private val chunker = mockk<DocChunker>()
    private val embeddingModel = mockk<EmbeddingModel>()

    private lateinit var processor: DocProcessor

    @BeforeEach
    fun setUp() {
        processor = DocProcessor(crawler, treeCrawler, converter, chunker, embeddingModel)
    }

    @Test
    fun `processSingle returns embedded chunks on success`() {
        val document = Jsoup.parse("<html><body><p>Hello</p></body></html>")
        every { crawler.crawlWithFallback(any(), any()) } returns CrawlResult(
            document = document,
            resolvedUrl = "https://docs.example.com/ref",
            failedUrls = emptyList(),
        )
        every { converter.convert(document) } returns "## Section\nHello content"
        every { chunker.chunk(any()) } returns listOf(
            DocChunker.ChunkResult("Hello content", "Section", "Section"),
        )
        every { embeddingModel.embed(any<List<String>>()) } returns listOf(anEmbedding())

        val result = processor.processSingle(
            "spring-boot",
            "4.0.0",
            DocType.REFERENCE,
            listOf("https://docs.example.com/ref"),
        )

        result.shouldNotBeNull()
        result shouldHaveSize 1
        result[0].text shouldBe "Hello content"
        result[0].embedding.shouldNotBeNull()
        result[0].metadata.library shouldBe "spring-boot"
    }

    @Test
    fun `processSingle returns null when crawl fails`() {
        every { crawler.crawlWithFallback(any(), any()) } returns CrawlResult(
            document = null,
            resolvedUrl = null,
            failedUrls = listOf("url1"),
        )

        val result = processor.processSingle(
            "spring-boot",
            "4.0.0",
            DocType.REFERENCE,
            listOf("url1"),
        )

        result.shouldBeNull()
    }

    @Test
    fun `processSingle returns null when content is blank`() {
        val document = Jsoup.parse("<html><body></body></html>")
        every { crawler.crawlWithFallback(any(), any()) } returns CrawlResult(
            document = document,
            resolvedUrl = "https://example.com",
            failedUrls = emptyList(),
        )
        every { converter.convert(document) } returns ""

        val result = processor.processSingle(
            "spring-boot",
            "4.0.0",
            DocType.REFERENCE,
            listOf("https://example.com"),
        )

        result.shouldBeNull()
    }

    @Test
    fun `processRecursive returns embedded chunks from tree crawl`() {
        val document = Jsoup.parse("<html><body><p>Page</p></body></html>")
        every { treeCrawler.crawlTree("https://docs.example.com", 2, any()) } returns listOf(
            TreeCrawlResult(document, "https://docs.example.com", 0),
            TreeCrawlResult(document, "https://docs.example.com/sub", 1),
        )
        every { converter.convert(document) } returns "## Page\nContent"
        every { chunker.chunk(any()) } returns listOf(
            DocChunker.ChunkResult("Content", "Page", "Page"),
        )
        every { embeddingModel.embed(any<List<String>>()) } returns listOf(anEmbedding(), anEmbedding())

        val result = processor.processRecursive(
            "spring-boot",
            "4.0.0",
            DocType.GUIDE,
            listOf("https://docs.example.com"),
            2,
        )

        result.shouldNotBeNull()
        result shouldHaveSize 2
    }

    @Test
    fun `processRecursive returns null when all seed URLs fail`() {
        every { treeCrawler.crawlTree(any(), any(), any()) } returns emptyList()

        val result = processor.processRecursive(
            "spring-boot",
            "4.0.0",
            DocType.GUIDE,
            listOf("https://invalid.com"),
            2,
        )

        result.shouldBeNull()
    }

    @Test
    fun `processRecursive tries next seed URL on empty result`() {
        val document = Jsoup.parse("<html><body><p>OK</p></body></html>")
        every { treeCrawler.crawlTree("https://fail.com", 2, any()) } returns emptyList()
        every { treeCrawler.crawlTree("https://ok.com", 2, any()) } returns listOf(
            TreeCrawlResult(document, "https://ok.com", 0),
        )
        every { converter.convert(document) } returns "## OK\nContent"
        every { chunker.chunk(any()) } returns listOf(
            DocChunker.ChunkResult("Content", "OK", "OK"),
        )
        every { embeddingModel.embed(any<List<String>>()) } returns listOf(anEmbedding())

        val result = processor.processRecursive(
            "lib",
            "1.0",
            DocType.REFERENCE,
            listOf("https://fail.com", "https://ok.com"),
            2,
        )

        result.shouldNotBeNull()
        result shouldHaveSize 1
    }
}
