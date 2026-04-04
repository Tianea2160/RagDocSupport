package org.tianea.ragdocsupport.crawler

import org.jsoup.nodes.Document
import us.codecraft.webmagic.ResultItems
import us.codecraft.webmagic.Task
import us.codecraft.webmagic.pipeline.Pipeline

internal class DocResultPipeline(
    private val results: MutableList<TreeCrawlResult>,
) : Pipeline {
    override fun process(
        resultItems: ResultItems,
        task: Task,
    ) {
        val document = resultItems.get<Document>(CrawlerConstants.FIELD_DOCUMENT) ?: return
        val url = resultItems.get<String>(CrawlerConstants.FIELD_URL) ?: return
        val depth = resultItems.get<Int>(CrawlerConstants.FIELD_DEPTH) ?: 0

        results.add(TreeCrawlResult(document, url, depth))
    }
}
