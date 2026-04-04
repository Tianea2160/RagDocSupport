package org.tianea.ragdocsupport.crawler

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.springframework.stereotype.Component

@Component
class HtmlToMarkdownConverter {
    fun convert(document: Document): String {
        // Try to find main content area
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

                is Element -> {
                    when (node.tagName().lowercase()) {
                        "h1" ->
                            sb
                                .appendLine()
                                .append("# ${node.text()}")
                                .appendLine()
                                .appendLine()

                        "h2" ->
                            sb
                                .appendLine()
                                .append("## ${node.text()}")
                                .appendLine()
                                .appendLine()

                        "h3" ->
                            sb
                                .appendLine()
                                .append("### ${node.text()}")
                                .appendLine()
                                .appendLine()

                        "h4" ->
                            sb
                                .appendLine()
                                .append("#### ${node.text()}")
                                .appendLine()
                                .appendLine()

                        "h5" ->
                            sb
                                .appendLine()
                                .append("##### ${node.text()}")
                                .appendLine()
                                .appendLine()

                        "h6" ->
                            sb
                                .appendLine()
                                .append("###### ${node.text()}")
                                .appendLine()
                                .appendLine()

                        "p" -> sb.append(convertElement(node)).appendLine().appendLine()

                        "pre" -> {
                            val code = node.selectFirst("code")?.text() ?: node.text()
                            val lang =
                                node
                                    .selectFirst("code")
                                    ?.className()
                                    ?.replace("language-", "")
                                    ?.replace("highlight", "")
                                    ?.trim() ?: ""
                            sb.appendLine("```$lang")
                            sb.appendLine(code)
                            sb.appendLine("```")
                            sb.appendLine()
                        }

                        "code" -> sb.append("`${node.text()}`")

                        "strong", "b" -> sb.append("**${node.text()}**")

                        "em", "i" -> sb.append("*${node.text()}*")

                        "a" -> {
                            val href = node.attr("href")
                            val text = node.text()
                            if (href.isNotBlank() && text.isNotBlank()) {
                                sb.append("[$text]($href)")
                            } else {
                                sb.append(text)
                            }
                        }

                        "ul" -> {
                            for (li in node.children()) {
                                sb.append("- ${li.text()}").appendLine()
                            }
                            sb.appendLine()
                        }

                        "ol" -> {
                            node.children().forEachIndexed { idx, li ->
                                sb.append("${idx + 1}. ${li.text()}").appendLine()
                            }
                            sb.appendLine()
                        }

                        "table" -> sb.append(convertTable(node)).appendLine()

                        "br" -> sb.appendLine()

                        "div", "section", "span" -> sb.append(convertElement(node))

                        "nav", "footer", "header", "script", "style" -> { /* skip */ }

                        else -> sb.append(convertElement(node))
                    }
                }
            }
        }

        return sb.toString()
    }

    private fun convertTable(table: Element): String {
        val sb = StringBuilder()
        val rows = table.select("tr")
        if (rows.isEmpty()) return ""

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
