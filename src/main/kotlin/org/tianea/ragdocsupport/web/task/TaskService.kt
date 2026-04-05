package org.tianea.ragdocsupport.web.task

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.tianea.ragdocsupport.sync.ProgressEvent
import org.tianea.ragdocsupport.sync.RegisterResult
import java.time.Instant
import java.util.UUID

@Service
class TaskService(
    private val taskRepository: TaskRepository,
    private val taskLogRepository: TaskLogRepository,
) {
    fun create(
        library: String,
        version: String,
        docUrl: String?,
    ): TaskEntity {
        val task =
            TaskEntity(
                id = UUID.randomUUID().toString().substring(0, 8),
                library = library,
                version = version,
                docUrl = docUrl,
            )
        return taskRepository.save(task)
    }

    @Transactional
    fun appendLog(
        taskId: String,
        event: ProgressEvent,
    ) {
        val task = taskRepository.findById(taskId).orElse(null) ?: return
        val sequence = taskLogRepository.countByTaskId(taskId)
        taskLogRepository.save(
            TaskLogEntity(
                task = task,
                sequence = sequence,
                type = event.type,
                message = event.message,
            ),
        )
    }

    @Transactional
    fun complete(
        taskId: String,
        result: RegisterResult,
    ) {
        val task = taskRepository.findById(taskId).orElse(null) ?: return
        task.status = TaskStatus.COMPLETED
        task.completedAt = Instant.now()
        task.chunksIndexed = result.chunksIndexed
        taskRepository.save(task)
    }

    @Transactional
    fun fail(
        taskId: String,
        error: String,
    ) {
        val task = taskRepository.findById(taskId).orElse(null) ?: return
        task.status = TaskStatus.FAILED
        task.completedAt = Instant.now()
        task.errorMessage = error
        taskRepository.save(task)
    }

    fun findAll(): List<TaskEntity> = taskRepository.findAllByOrderByStartedAtDesc()

    fun findById(id: String): TaskEntity? = taskRepository.findById(id).orElse(null)

    fun findRunning(): List<TaskEntity> = taskRepository.findByStatus(TaskStatus.RUNNING)

    fun getLogsSince(
        taskId: String,
        afterSequence: Int,
    ): List<TaskLogEntity> = taskLogRepository.findByTaskIdAndSequenceGreaterThanOrderBySequenceAsc(taskId, afterSequence)
}
