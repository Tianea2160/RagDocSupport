package org.tianea.ragdocsupport.core.model

data class DocSource(
    val library: String,
    val group: String,
    val artifact: String? = null,
    val docs: Map<DocType, DocUrlPattern>,
)

data class DocUrlPattern(
    val urlTemplates: List<String>,
) {
    constructor(urlTemplate: String) : this(listOf(urlTemplate))

    fun resolveAll(dependency: Dependency): List<String> = urlTemplates.map { it.resolve(dependency) }

    private fun String.resolve(dependency: Dependency): String = this
        .replace("{version}", dependency.version)
        .replace("{major}", dependency.major.toString())
        .replace("{majorMinor}", dependency.majorMinor)
}
