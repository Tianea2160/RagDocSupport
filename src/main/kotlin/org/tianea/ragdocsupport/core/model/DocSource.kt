package org.tianea.ragdocsupport.core.model

data class DocSource(
    val library: String,
    val group: String,
    val artifact: String? = null,
    val docs: Map<DocType, DocUrlPattern>,
)

data class DocUrlPattern(
    val urlTemplate: String,
) {
    fun resolve(dependency: Dependency): String =
        urlTemplate
            .replace("{version}", dependency.version)
            .replace("{major}", dependency.major.toString())
            .replace("{majorMinor}", dependency.majorMinor)
}
