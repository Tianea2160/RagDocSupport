package org.tianea.ragdocsupport.web.task

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "tasks")
class TaskEntity(
    @Id
    val id: String,
    val library: String,
    val version: String,
    val docUrl: String? = null,
    @Enumerated(EnumType.STRING)
    var status: TaskStatus = TaskStatus.RUNNING,
    val startedAt: Instant = Instant.now(),
    var completedAt: Instant? = null,
    var chunksIndexed: Int = 0,
    var errorMessage: String? = null,
    @OneToMany(mappedBy = "task", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @OrderBy("sequence ASC")
    val logs: MutableList<TaskLogEntity> = mutableListOf(),
)

enum class TaskStatus {
    RUNNING,
    COMPLETED,
    FAILED,
}
