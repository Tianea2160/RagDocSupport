package org.tianea.ragdocsupport.core.model

data class DocSource(
    val library: String,
    val group: String,
    val artifact: String? = null,
    val docs: Map<DocType, DocUrlPattern>,
)

private const val DEFAULT_MAX_PAGES = 100

data class DocUrlPattern(
    val urlTemplates: List<String>,
    val recursive: Boolean = true,
    val maxDepth: Int = 2,
    val maxPages: Int = DEFAULT_MAX_PAGES,
) {
    constructor(urlTemplate: String) : this(listOf(urlTemplate))

    fun resolveAll(dependency: Dependency): List<String> = urlTemplates.map { it.resolve(dependency) }

    private fun String.resolve(dependency: Dependency): String = this
        .replace("{version}", dependency.version)
        .replace("{major}", dependency.major.toString())
        .replace("{majorMinor}", dependency.majorMinor)
}
