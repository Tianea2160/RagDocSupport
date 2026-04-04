package org.tianea.ragdocsupport.crawler

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.springframework.stereotype.Component

@Component
class HtmlToMarkdownConverter {
    fun convert(document: Document): String {
        val content =
            document.selectFirst("main, article, .content, #content, .documentation, #main-content")
                ?: document.body()

        return convertElement(content).trim()
    }

    private fun convertElement(element: Element): String {
        val sb = StringBuilder()

        for (node in element.childNodes()) {
            when (node) {
                is TextNode -> sb.append(node.text())
                is Element -> sb.append(convertTag(node))
            }
        }

        return sb.toString()
    }

    private fun convertTag(element: Element): String = when (element.tagName().lowercase()) {
        "h1", "h2", "h3", "h4", "h5", "h6" -> convertHeader(element)
        "p" -> convertElement(element) + "\n\n"
        "pre" -> convertCodeBlock(element)
        "code" -> "`${element.text()}`"
        "strong", "b" -> "**${element.text()}**"
        "em", "i" -> "*${element.text()}*"
        "a" -> convertLink(element)
        "ul" -> convertUnorderedList(element)
        "ol" -> convertOrderedList(element)
        "table" -> convertTable(element) + "\n"
        "br" -> "\n"
        "div", "section", "span" -> convertElement(element)
        "nav", "footer", "header", "script", "style" -> ""
        else -> convertElement(element)
    }

    private fun convertHeader(element: Element): String {
        val level = element.tagName().removePrefix("h").toInt()
        val prefix = "#".repeat(level)
        return "\n$prefix ${element.text()}\n\n"
    }

    private fun convertCodeBlock(element: Element): String {
        val codeElement = element.selectFirst("code")
        val code = codeElement?.text() ?: element.text()
        val lang =
            codeElement
                ?.className()
                ?.replace("language-", "")
                ?.replace("highlight", "")
                ?.trim() ?: ""
        return "```$lang\n$code\n```\n\n"
    }

    private fun convertLink(element: Element): String {
        val href = element.attr("href")
        val text = element.text()
        return if (href.isNotBlank() && text.isNotBlank()) "[$text]($href)" else text
    }

    private fun convertUnorderedList(element: Element): String = element.children().joinToString("") { "- ${it.text()}\n" } + "\n"

    private fun convertOrderedList(element: Element): String = element.children().mapIndexed { idx, li ->
        "${idx + 1}. ${li.text()}\n"
    }.joinToString("") + "\n"

    private fun convertTable(table: Element): String {
        val rows = table.select("tr")
        if (rows.isEmpty()) return ""

        val sb = StringBuilder()
        for ((idx, row) in rows.withIndex()) {
            val cells = row.select("th, td")
            sb.append("| ${cells.joinToString(" | ") { it.text() }} |").appendLine()
            if (idx == 0) {
                sb.append("| ${cells.joinToString(" | ") { "---" }} |").appendLine()
            }
        }
        sb.appendLine()
        return sb.toString()
    }
}
