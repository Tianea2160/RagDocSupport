package org.tianea.ragdocsupport.mcp

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.any
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.tianea.ragdocsupport.core.model.DocType
import org.tianea.ragdocsupport.sync.DocSyncService
import org.tianea.ragdocsupport.sync.FailedDocType
import org.tianea.ragdocsupport.sync.RegisterResult

class DocsRegisterToolTest {
    private val syncService = mockk<DocSyncService>()
    private val tool = DocsRegisterTool(syncService)

    @Test
    fun `returns success message with chunk count`() {
        every { syncService.register("spring-boot", "4.0.0", null, any()) } returns RegisterResult(
            success = true,
            chunksIndexed = 42,
        )

        val result = tool.docsRegister("spring-boot", "4.0.0", null)

        result shouldContain "Successfully indexed 42 chunks"
        result shouldContain "spring-boot:4.0.0"
    }

    @Test
    fun `returns failure message`() {
        every { syncService.register("unknown", "1.0", null, any()) } returns RegisterResult(success = false)

        val result = tool.docsRegister("unknown", "1.0", null)

        result shouldContain "Failed to index"
    }

    @Test
    fun `includes failed doc types in result`() {
        every { syncService.register("kafka", "3.7.0", null, any()) } returns RegisterResult(
            success = true,
            chunksIndexed = 10,
            failedDocTypes = listOf(FailedDocType(DocType.MIGRATION, listOf("https://url1", "https://url2"))),
        )

        val result = tool.docsRegister("kafka", "3.7.0", null)

        result shouldContain "Successfully indexed"
        result shouldContain "Failed doc types"
        result shouldContain "MIGRATION"
        result shouldContain "https://url1"
    }

    @Test
    fun `does not show failed section when all succeed`() {
        every { syncService.register("lib", "1.0", null, any()) } returns RegisterResult(
            success = true,
            chunksIndexed = 5,
        )

        val result = tool.docsRegister("lib", "1.0", null)

        result shouldNotContain "Failed doc types"
    }

    @Test
    fun `passes explicit docUrl to service`() {
        every {
            syncService.register("mylib", "1.0", "https://custom.com/docs", any())
        } returns RegisterResult(success = true, chunksIndexed = 3)

        val result = tool.docsRegister("mylib", "1.0", "https://custom.com/docs")

        result shouldContain "Successfully indexed 3 chunks"
    }
}
