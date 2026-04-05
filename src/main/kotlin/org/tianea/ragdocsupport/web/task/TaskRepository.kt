package org.tianea.ragdocsupport.web.task

import org.springframework.data.jpa.repository.JpaRepository

interface TaskRepository : JpaRepository<TaskEntity, String> {
    fun findByStatus(status: TaskStatus): List<TaskEntity>

    fun findAllByOrderByStartedAtDesc(): List<TaskEntity>
}

interface TaskLogRepository : JpaRepository<TaskLogEntity, Long> {
    fun countByTaskId(taskId: String): Int

    fun findByTaskIdAndSequenceGreaterThanOrderBySequenceAsc(
        taskId: String,
        sequence: Int,
    ): List<TaskLogEntity>
}
