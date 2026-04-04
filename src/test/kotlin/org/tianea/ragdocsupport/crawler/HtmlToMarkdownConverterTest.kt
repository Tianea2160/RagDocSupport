package org.tianea.ragdocsupport.crawler

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test

class HtmlToMarkdownConverterTest {
    private val converter = HtmlToMarkdownConverter()

    private fun html(body: String) = Jsoup.parse("<html><body>$body</body></html>")

    @Test
    fun `converts h1 through h6 headers`() {
        val doc = html("<h1>Title</h1><h2>Sub</h2><h3>Sub2</h3><h4>H4</h4><h5>H5</h5><h6>H6</h6>")
        val result = converter.convert(doc)

        result shouldContain "# Title"
        result shouldContain "## Sub"
        result shouldContain "### Sub2"
        result shouldContain "#### H4"
        result shouldContain "##### H5"
        result shouldContain "###### H6"
    }

    @Test
    fun `converts paragraphs`() {
        val doc = html("<p>Hello world</p>")
        val result = converter.convert(doc)

        result shouldContain "Hello world"
    }

    @Test
    fun `converts code blocks with language`() {
        val doc = html("<pre><code class=\"language-kotlin\">fun main() {}</code></pre>")
        val result = converter.convert(doc)

        result shouldContain "```kotlin"
        result shouldContain "fun main() {}"
        result shouldContain "```"
    }

    @Test
    fun `converts inline code`() {
        val doc = html("<p>Use <code>val x = 1</code> syntax</p>")
        val result = converter.convert(doc)

        result shouldContain "`val x = 1`"
    }

    @Test
    fun `converts bold and italic`() {
        val doc = html("<p><strong>bold</strong> and <em>italic</em></p>")
        val result = converter.convert(doc)

        result shouldContain "**bold**"
        result shouldContain "*italic*"
    }

    @Test
    fun `converts links`() {
        val doc = html("<a href=\"https://example.com\">Click here</a>")
        val result = converter.convert(doc)

        result shouldContain "[Click here](https://example.com)"
    }

    @Test
    fun `converts link with empty href as plain text`() {
        val doc = html("<a href=\"\">No link</a>")
        val result = converter.convert(doc)

        result shouldContain "No link"
        result shouldNotContain "["
    }

    @Test
    fun `converts unordered list`() {
        val doc = html("<ul><li>Item 1</li><li>Item 2</li></ul>")
        val result = converter.convert(doc)

        result shouldContain "- Item 1"
        result shouldContain "- Item 2"
    }

    @Test
    fun `converts ordered list`() {
        val doc = html("<ol><li>First</li><li>Second</li></ol>")
        val result = converter.convert(doc)

        result shouldContain "1. First"
        result shouldContain "2. Second"
    }

    @Test
    fun `converts table with header row`() {
        val doc = html(
            """
            <table>
                <tr><th>Name</th><th>Value</th></tr>
                <tr><td>key</td><td>val</td></tr>
            </table>
            """,
        )
        val result = converter.convert(doc)

        result shouldContain "| Name | Value |"
        result shouldContain "| --- | --- |"
        result shouldContain "| key | val |"
    }

    @Test
    fun `skips nav, footer, header, script, style`() {
        val doc = html(
            "<p>Content</p><nav>Nav</nav><footer>Foot</footer><script>js()</script><style>.x{}</style>",
        )
        val result = converter.convert(doc)

        result shouldContain "Content"
        result shouldNotContain "Nav"
        result shouldNotContain "Foot"
        result shouldNotContain "js()"
        result shouldNotContain ".x{}"
    }

    @Test
    fun `prefers main content area over body`() {
        val doc = Jsoup.parse(
            "<html><body><div>Noise</div><main><p>Main content</p></main></body></html>",
        )
        val result = converter.convert(doc)

        result shouldContain "Main content"
        result shouldNotContain "Noise"
    }

    @Test
    fun `converts br to newline`() {
        val doc = html("Line1<br>Line2")
        val result = converter.convert(doc)

        result shouldContain "Line1"
        result shouldContain "Line2"
    }
}
