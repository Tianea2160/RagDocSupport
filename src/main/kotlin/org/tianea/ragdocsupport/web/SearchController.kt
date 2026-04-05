package org.tianea.ragdocsupport.web

import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.tianea.ragdocsupport.core.port.VectorStore

@Controller
class SearchController(
    private val vectorStore: VectorStore,
    private val embeddingModel: EmbeddingModel,
) {
    @GetMapping("/web/search")
    fun searchPage(): String = "search"

    @GetMapping("/web/search/results")
    fun searchResults(
        @RequestParam query: String,
        @RequestParam(required = false) library: String?,
        @RequestParam(required = false) version: String?,
        model: Model,
    ): String {
        val queryEmbedding = embeddingModel.embed(query)
        val results =
            vectorStore.search(
                queryEmbedding,
                library?.takeIf { it.isNotBlank() },
                version?.takeIf { it.isNotBlank() },
            )
        model.addAttribute("results", results)
        return "fragments/search-results :: results"
    }
}
