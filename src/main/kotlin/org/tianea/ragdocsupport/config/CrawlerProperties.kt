package org.tianea.ragdocsupport.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "crawler")
data class CrawlerProperties(
    val sleepTimeMs: Int = 100,
    val timeoutMs: Int = 30_000,
    val retryTimes: Int = 2,
)
