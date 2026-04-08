package org.tianea.ragdocsupport.web.task

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.stereotype.Component
import org.tianea.ragdocsupport.sync.ProgressEvent
import org.tianea.ragdocsupport.sync.RegisterResult
import java.util.concurrent.LinkedBlockingQueue

@Component
class TaskLogWriter(
    private val taskService: TaskService,
) : DisposableBean {
    private val log = LoggerFactory.getLogger(javaClass)
    private val queue = LinkedBlockingQueue<TaskWrite>()
    private val writerThread =
        Thread.startVirtualThread {
            processLoop()
        }

    sealed interface TaskWrite

    private data class AppendLog(val taskId: String, val event: ProgressEvent) : TaskWrite

    private data class Complete(val taskId: String, val result: RegisterResult) : TaskWrite

    private data class Fail(val taskId: String, val error: String) : TaskWrite

    private data object Shutdown : TaskWrite

    fun submitLog(
        taskId: String,
        event: ProgressEvent,
    ) {
        queue.put(AppendLog(taskId, event))
    }

    fun submitComplete(
        taskId: String,
        result: RegisterResult,
    ) {
        queue.put(Complete(taskId, result))
    }

    fun submitFail(
        taskId: String,
        error: String,
    ) {
        queue.put(Fail(taskId, error))
    }

    private fun processLoop() {
        while (true) {
            try {
                when (val write = queue.take()) {
                    is AppendLog -> taskService.appendLog(write.taskId, write.event)
                    is Complete -> taskService.complete(write.taskId, write.result)
                    is Fail -> taskService.fail(write.taskId, write.error)
                    is Shutdown -> return
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            } catch (e: Exception) {
                log.warn("Failed to write task log: ${e.message}")
            }
        }
    }

    override fun destroy() {
        queue.put(Shutdown)
        writerThread.join(5000)
    }
}
