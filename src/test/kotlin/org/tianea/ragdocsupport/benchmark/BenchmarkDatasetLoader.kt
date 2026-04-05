package org.tianea.ragdocsupport.benchmark

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory

object BenchmarkDatasetLoader {
    private val mapper = ObjectMapper(YAMLFactory())

    fun load(resourcePath: String = "benchmark/benchmark-queries.yml"): BenchmarkDataset {
        val input = Thread.currentThread().contextClassLoader
            .getResourceAsStream(resourcePath)
            ?: throw IllegalStateException("Benchmark dataset not found: $resourcePath")

        val tree = mapper.readTree(input)
        val queriesNode = tree["queries"]
            ?: throw IllegalStateException("No 'queries' field in benchmark dataset")

        val queries = queriesNode.map { node ->
            BenchmarkQuery(
                query = node["query"].asText(),
                library = node["library"].asText(),
                category = node["category"].asText(),
                difficulty = node["difficulty"].asText(),
            )
        }

        return BenchmarkDataset(queries)
    }
}
