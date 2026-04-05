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
import org.tianea.ragdocsupport.web.task.TaskService
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

@Controller
class RegisterController(
    private val syncService: DocSyncService,
    private val docSourceRepository: DocSourceRepository,
    private val taskService: TaskService,
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
            taskService.appendLog(task.id, event)
            trySend(emitter, closed, "log", renderLogLine(event))
        }

        Thread.startVirtualThread {
            try {
                val result = syncService.register(library, version, docUrl, listener)
                taskService.complete(task.id, result)
                trySend(emitter, closed, "complete", renderResult(result, task.id))
            } catch (e: Exception) {
                taskService.fail(task.id, e.message ?: "Unknown error")
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
                    for (taskId in taskIds) {
                        taskService.appendLog(taskId, event)
                    }
                    trySend(emitter, closed, "log", renderLogLine(event))
                }
                val result = syncService.registerBulk(requests, listener)
                for ((index, entry) in result.entries.withIndex()) {
                    if (index < taskIds.size) {
                        if (entry.result.success) {
                            taskService.complete(taskIds[index], entry.result)
                        } else {
                            taskService.fail(taskIds[index], "No chunks indexed")
                        }
                    }
                }
                val summary =
                    "<div class=\"p-4 bg-green-50 border border-green-200 rounded-lg\">" +
                        "<p class=\"font-semibold text-green-800\">Bulk registration complete</p>" +
                        "<p class=\"text-sm text-green-700\">" +
                        "${result.successCount}/${result.entries.size} succeeded, " +
                        "${result.totalChunks} total chunks</p>" +
                        "<a href=\"/web/tasks\" class=\"text-sm text-blue-600 hover:underline\">View tasks</a>" +
                        "</div>"
                trySend(emitter, closed, "complete", summary)
            } catch (e: Exception) {
                for (taskId in taskIds) {
                    taskService.fail(taskId, e.message ?: "Unknown error")
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
                "INFO" to "text-gray-300",
                "CRAWL" to "text-blue-400",
                "CHUNK" to "text-purple-400",
                "EMBED" to "text-cyan-400",
                "UPSERT" to "text-green-400",
                "WARN" to "text-yellow-400",
                "COMPLETE" to "text-green-400",
                "ERROR" to "text-red-400",
            )

        fun renderLogLine(event: ProgressEvent): String {
            val time = LocalTime.now().format(TIME_FORMAT)
            val colorClass = TYPE_COLORS[event.type.name] ?: "text-gray-300"
            return "<div class=\"flex gap-2\">" +
                "<span class=\"text-gray-500\">$time</span>" +
                "<span class=\"$colorClass\">[${event.type}]</span>" +
                "<span class=\"text-gray-200\">${escapeHtml(event.message)}</span>" +
                "</div>"
        }

        fun renderResult(
            result: RegisterResult,
            taskId: String,
        ): String {
            val statusClass = if (result.success) "bg-green-50 border-green-200" else "bg-red-50 border-red-200"
            val textClass = if (result.success) "text-green-800" else "text-red-800"
            val subTextClass = if (result.success) "text-green-700" else "text-red-700"
            val statusText = if (result.success) "Registration complete" else "Registration failed"
            return "<div class=\"p-4 $statusClass border rounded-lg\">" +
                "<p class=\"font-semibold $textClass\">$statusText</p>" +
                "<p class=\"text-sm $subTextClass\">${result.chunksIndexed} chunks indexed</p>" +
                (
                    if (result.failedDocTypes.isNotEmpty()) {
                        "<p class=\"text-sm text-red-600 mt-1\">Failed: ${result.failedDocTypes.joinToString { it.docType.name }}</p>"
                    } else {
                        ""
                    }
                    ) +
                "<a href=\"/web/tasks/$taskId\" class=\"text-sm text-blue-600 hover:underline mt-2 inline-block\">View task details</a>" +
                "</div>"
        }

        fun renderError(e: Exception): String =
            "<div class=\"p-4 bg-red-50 border border-red-200 rounded-lg\">" +
                "<p class=\"font-semibold text-red-800\">Error</p>" +
                "<p class=\"text-sm text-red-700\">${escapeHtml(e.message ?: "Unknown error")}</p>" +
                "</div>"

        private fun escapeHtml(text: String): String =
            text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
        }
}
