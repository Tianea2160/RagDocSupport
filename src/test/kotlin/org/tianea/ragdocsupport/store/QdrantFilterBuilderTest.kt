package org.tianea.ragdocsupport.store

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class QdrantFilterBuilderTest {
    @Test
    fun `libraryVersionFilter creates filter with library and version`() {
        val filter = QdrantFilterBuilder.libraryVersionFilter("spring-boot", "4.0.0")

        filter.mustList shouldHaveSize 2
        filter.mustList[0].field.key shouldBe "library"
        filter.mustList[0].field.match.keyword shouldBe "spring-boot"
        filter.mustList[1].field.key shouldBe "version"
        filter.mustList[1].field.match.keyword shouldBe "4.0.0"
    }

    @Test
    fun `searchFilter with both library and version`() {
        val filter = QdrantFilterBuilder.searchFilter("kafka", "3.7.0")

        filter.mustList shouldHaveSize 2
        filter.mustList[0].field.key shouldBe "library"
        filter.mustList[1].field.key shouldBe "version"
    }

    @Test
    fun `searchFilter with null library and version creates empty filter`() {
        val filter = QdrantFilterBuilder.searchFilter(null, null)

        filter.mustList shouldHaveSize 0
    }

    @Test
    fun `searchFilter with only library`() {
        val filter = QdrantFilterBuilder.searchFilter("spring-boot", null)

        filter.mustList shouldHaveSize 1
        filter.mustList[0].field.key shouldBe "library"
    }

    @Test
    fun `searchFilter with only version`() {
        val filter = QdrantFilterBuilder.searchFilter(null, "4.0.0")

        filter.mustList shouldHaveSize 1
        filter.mustList[0].field.key shouldBe "version"
    }

    @Test
    fun `multiVersionFilter creates OR filter for versions under library AND`() {
        val filter = QdrantFilterBuilder.multiVersionFilter("spring-boot", listOf("3.0.0", "4.0.0"))

        filter.mustList shouldHaveSize 2
        filter.mustList[0].field.key shouldBe "library"

        val versionFilter = filter.mustList[1].filter
        versionFilter.shouldList shouldHaveSize 2
        versionFilter.shouldList[0].field.match.keyword shouldBe "3.0.0"
        versionFilter.shouldList[1].field.match.keyword shouldBe "4.0.0"
    }
}
