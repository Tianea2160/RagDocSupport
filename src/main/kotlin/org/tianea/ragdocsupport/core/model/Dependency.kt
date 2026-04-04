package org.tianea.ragdocsupport.core.model

data class Dependency(
    val group: String,
    val artifact: String,
    val version: String,
) {
    val major: Int get() = version.split(".").getOrNull(0)?.toIntOrNull() ?: 0
    val minor: Int get() = version.split(".").getOrNull(1)?.toIntOrNull() ?: 0
    val majorMinor: String get() = "$major.$minor"
}
