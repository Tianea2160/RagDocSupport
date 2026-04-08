package org.tianea.ragdocsupport.web

import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import org.tianea.ragdocsupport.core.port.DocSourceRepository
import org.tianea.ragdocsupport.sync.BulkRegisterRequest
import org.tianea.ragdocsupport.sync.DocSyncService
import org.tianea.ragdocsupport.sync.ProgressEvent
import org.tianea.ragdocsupport.sync.ProgressListener
import org.tianea.ragdocsupport.sync.RegisterResult
import org.tianea.ragdocsupport.web.task.TaskLogWriter
import org.tianea.ragdocsupport.web.task.TaskService
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

@Controller
class RegisterController(
    private val syncService: DocSyncService,
    private val docSourceRepository: DocSourceRepository,
    private val taskService: TaskService,
    private val taskLogWriter: TaskLogWriter,
) {
    @GetMapping("/web/register")
    fun registerPage(model: Model): String {
        model.addAttribute("libraries", docSourceRepository.findAll().map { it.library })
        return "register"
    }

    @GetMapping("/web/register/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @ResponseBody
    fun registerStream(
        @RequestParam library: String,
        @RequestParam version: String,
        @RequestParam(required = false) docUrl: String?,
    ): SseEmitter {
        val task = taskService.create(library, version, docUrl)
        val emitter = SseEmitter(1_800_000L)
        val closed = AtomicBoolean(false)
        emitter.onCompletion { closed.set(true) }
        emitter.onTimeout { closed.set(true) }
        emitter.onError { closed.set(true) }

        trySend(emitter, closed, "taskId", task.id)

        val listener = ProgressListener { event ->
            trySend(emitter, closed, "log", renderLogLine(event))
            taskLogWriter.submitLog(task.id, event)
        }

        Thread.startVirtualThread {
            try {
                val result = syncService.register(library, version, docUrl, listener)
                taskLogWriter.submitComplete(task.id, result)
                trySend(emitter, closed, "complete", renderResult(result, task.id))
            } catch (e: Exception) {
                taskLogWriter.submitFail(task.id, e.message ?: "Unknown error")
                trySend(emitter, closed, "error", renderError(e))
            } finally {
                if (!closed.get()) emitter.complete()
            }
        }
        return emitter
    }

    @GetMapping("/web/register/stream/bulk", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @ResponseBody
    fun registerBulkStream(
        @RequestParam input: String,
    ): SseEmitter {
        val emitter = SseEmitter(1_800_000L)
        val closed = AtomicBoolean(false)
        emitter.onCompletion { closed.set(true) }
        emitter.onTimeout { closed.set(true) }
        emitter.onError { closed.set(true) }

        val requests =
            input
                .lines()
                .filter { it.contains(":") }
                .map { line ->
                    val (lib, ver) = line.trim().split(":", limit = 2)
                    BulkRegisterRequest(lib.trim(), ver.trim())
                }

        if (requests.isEmpty()) {
            trySend(emitter, closed, "error", renderError(IllegalArgumentException("No valid entries")))
            emitter.complete()
            return emitter
        }

        val taskIds = mutableListOf<String>()
        for (req in requests) {
            val task = taskService.create(req.library, req.version, req.docUrl)
            taskIds.add(task.id)
        }
        trySend(emitter, closed, "taskIds", taskIds.joinToString(","))

        Thread.startVirtualThread {
            try {
                val listener = ProgressListener { event ->
                    trySend(emitter, closed, "log", renderLogLine(event))
                    for (taskId in taskIds) {
                        taskLogWriter.submitLog(taskId, event)
                    }
                }
                val result = syncService.registerBulk(requests, listener)
                for ((index, entry) in result.entries.withIndex()) {
                    if (index < taskIds.size) {
                        if (entry.result.success) {
                            taskLogWriter.submitComplete(taskIds[index], entry.result)
                        } else {
                            taskLogWriter.submitFail(taskIds[index], "No chunks indexed")
                        }
                    }
                }
                val summary =
                    "<div class=\"p-5 bg-ok/5 border border-ok/30 rounded-xl mt-2 animate-slide-up\">" +
                        "<div class=\"flex items-center gap-2 mb-2\">" +
                        "<span class=\"text-lg text-ok\">&#x2713;</span>" +
                        "<p class=\"font-semibold text-ok\">Bulk registration complete</p>" +
                        "</div>" +
                        "<p class=\"text-sm text-surface-200/60 font-mono\">" +
                        "${result.successCount}/${result.entries.size} succeeded, " +
                        "${result.totalChunks} total chunks</p>" +
                        "<a href=\"/web/tasks\" class=\"text-sm text-accent/70 hover:text-accent font-mono mt-3 inline-block transition-colors\">View tasks &rarr;</a>" +
                        "</div>"
                trySend(emitter, closed, "complete", summary)
            } catch (e: Exception) {
                for (taskId in taskIds) {
                    taskLogWriter.submitFail(taskId, e.message ?: "Unknown error")
                }
                trySend(emitter, closed, "error", renderError(e))
            } finally {
                if (!closed.get()) emitter.complete()
            }
        }
        return emitter
    }

    private fun trySend(
        emitter: SseEmitter,
        closed: AtomicBoolean,
        eventName: String,
        data: String,
    ) {
        if (closed.get()) return
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data))
        } catch (_: Exception) {
            closed.set(true)
        }
    }

    companion object {
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")

        private val TYPE_COLORS =
            mapOf(
                "INFO" to "text-surface-200/50",
                "CRAWL" to "text-run",
                "CHUNK" to "text-purple-400",
                "EMBED" to "text-accent",
                "UPSERT" to "text-ok",
                "WARN" to "text-warn",
                "COMPLETE" to "text-ok",
                "ERROR" to "text-fail",
            )

        fun renderLogLine(event: ProgressEvent): String {
            val time = LocalTime.now().format(TIME_FORMAT)
            val colorClass = TYPE_COLORS[event.type.name] ?: "text-surface-200/50"
            return "<div class=\"flex gap-2\">" +
                "<span class=\"text-surface-200/30 shrink-0\">$time</span>" +
                "<span class=\"$colorClass shrink-0\">[${event.type}]</span>" +
                "<span class=\"text-surface-200/70\">${escapeHtml(event.message)}</span>" +
                "</div>"
        }

        fun renderResult(
            result: RegisterResult,
            taskId: String,
        ): String {
            val (borderColor, bgColor, textColor, icon) =
                if (result.success) {
                    listOf("border-ok/30", "bg-ok/5", "text-ok", "&#x2713;")
                } else {
                    listOf("border-fail/30", "bg-fail/5", "text-fail", "&#x2717;")
                }
            val statusText = if (result.success) "Registration complete" else "Registration failed"
            return "<div class=\"p-5 $bgColor border $borderColor rounded-xl mt-2 animate-slide-up\">" +
                "<div class=\"flex items-center gap-2 mb-2\">" +
                "<span class=\"text-lg $textColor\">$icon</span>" +
                "<p class=\"font-semibold $textColor\">${escapeHtml(statusText)}</p>" +
                "</div>" +
                "<p class=\"text-sm text-surface-200/60 font-mono\">${result.chunksIndexed} chunks indexed</p>" +
                (
                    if (result.failedDocTypes.isNotEmpty()) {
                        "<p class=\"text-sm text-fail/70 mt-1 font-mono\">Failed: ${escapeHtml(result.failedDocTypes.joinToString { it.docType.name })}</p>"
                    } else {
                        ""
                    }
                    ) +
                "<a href=\"/web/tasks/$taskId\" class=\"text-sm text-accent/70 hover:text-accent font-mono mt-3 inline-block transition-colors\">View task &rarr;</a>" +
                "</div>"
        }

        fun renderError(e: Exception): String = "<div class=\"p-5 bg-fail/5 border border-fail/30 rounded-xl mt-2\">" +
            "<div class=\"flex items-center gap-2 mb-2\">" +
            "<span class=\"text-lg text-fail\">&#x2717;</span>" +
            "<p class=\"font-semibold text-fail\">Error</p>" +
            "</div>" +
            "<p class=\"text-sm text-surface-200/60 font-mono\">${escapeHtml(e.message ?: "Unknown error")}</p>" +
            "</div>"

        private fun escapeHtml(text: String): String = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
