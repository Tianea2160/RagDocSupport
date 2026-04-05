package org.tianea.ragdocsupport.benchmark

data class BenchmarkDataset(
    val queries: List<BenchmarkQuery>,
)

data class BenchmarkQuery(
    val query: String,
    val library: String,
    val category: String,
    val difficulty: String,
)
