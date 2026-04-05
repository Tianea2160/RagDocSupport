package org.tianea.ragdocsupport.web.task

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.tianea.ragdocsupport.sync.ProgressEventType
import java.time.Instant

@Entity
@Table(name = "task_logs")
class TaskLogEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    val task: TaskEntity,
    val sequence: Int,
    val timestamp: Instant = Instant.now(),
    @Enumerated(EnumType.STRING)
    val type: ProgressEventType,
    @Column(columnDefinition = "TEXT")
    val message: String,
)
