package org.tianea.ragdocsupport.web

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.tianea.ragdocsupport.core.port.VectorStore
import org.tianea.ragdocsupport.web.task.TaskService

@Controller
class DashboardController(
    private val vectorStore: VectorStore,
    private val taskService: TaskService,
) {
    @GetMapping("/", "/web")
    fun index(model: Model): String {
        model.addAttribute("libraries", vectorStore.listIndexedLibraries())
        model.addAttribute("runningTasks", taskService.findRunning())
        model.addAttribute("recentTasks", taskService.findAll().take(5))
        return "index"
    }
}
