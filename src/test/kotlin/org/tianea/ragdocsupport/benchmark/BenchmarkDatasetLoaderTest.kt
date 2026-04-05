package org.tianea.ragdocsupport.benchmark

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BenchmarkDatasetLoaderTest {
    @Test
    fun `loads benchmark queries from default resource`() {
        val dataset = BenchmarkDatasetLoader.load()

        dataset.queries.shouldNotBeEmpty()
        dataset.queries.forEach { query ->
            query.query.shouldNotBeBlank()
            query.library.shouldNotBeBlank()
            query.category.shouldNotBeBlank()
            query.difficulty.shouldNotBeBlank()
        }
    }

    @Test
    fun `queries cover multiple libraries`() {
        val dataset = BenchmarkDatasetLoader.load()
        val libraries = dataset.queries.map { it.library }.toSet()

        (libraries.size > 1) shouldBe true
    }

    @Test
    fun `queries cover multiple difficulty levels`() {
        val dataset = BenchmarkDatasetLoader.load()
        val difficulties = dataset.queries.map { it.difficulty }.toSet()

        difficulties shouldBe setOf("easy", "medium", "hard")
    }

    @Test
    fun `throws when resource not found`() {
        assertThrows<IllegalStateException> {
            BenchmarkDatasetLoader.load("nonexistent.yml")
        }
    }
}
