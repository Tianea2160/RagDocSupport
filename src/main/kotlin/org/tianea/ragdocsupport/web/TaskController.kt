package org.tianea.ragdocsupport.web

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException
import org.tianea.ragdocsupport.web.task.TaskService

@Controller
class TaskController(
    private val taskService: TaskService,
) {
    @GetMapping("/web/tasks")
    fun tasks(model: Model): String {
        model.addAttribute("tasks", taskService.findAll())
        return "tasks"
    }

    @GetMapping("/web/tasks/{id}")
    fun taskDetail(
        @PathVariable id: String,
        model: Model,
    ): String {
        val task = taskService.findById(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        model.addAttribute("task", task)
        model.addAttribute("logs", taskService.getLogsSince(id, -1))
        return "task-detail"
    }

    @GetMapping("/web/tasks/{id}/logs")
    fun taskLogs(
        @PathVariable id: String,
        @RequestParam(defaultValue = "-1") afterSequence: Int,
        model: Model,
    ): String {
        val newLogs = taskService.getLogsSince(id, afterSequence)
        val task = taskService.findById(id)
        model.addAttribute("logs", newLogs)
        model.addAttribute(
            "nextSequence",
            if (newLogs.isNotEmpty()) newLogs.last().sequence else afterSequence,
        )
        model.addAttribute("status", task?.status)
        return "fragments/task-logs :: logs"
    }
}
