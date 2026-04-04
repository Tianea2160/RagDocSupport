package org.tianea.ragdocsupport.fixture

import org.tianea.ragdocsupport.core.model.Dependency
import org.tianea.ragdocsupport.core.model.DocSource
import org.tianea.ragdocsupport.core.model.DocType
import org.tianea.ragdocsupport.core.model.DocUrlPattern

fun aDocSource(
    library: String = "spring-boot",
    group: String = "org.springframework.boot",
    artifact: String? = "spring-boot",
    docs: Map<DocType, DocUrlPattern> = mapOf(
        DocType.REFERENCE to aDocUrlPattern(),
    ),
) = DocSource(library = library, group = group, artifact = artifact, docs = docs)

fun aDocUrlPattern(
    urlTemplates: List<String> = listOf("https://docs.example.com/{version}/reference"),
    recursive: Boolean = false,
    maxDepth: Int = 2,
    maxPages: Int = 100,
) = DocUrlPattern(urlTemplates = urlTemplates, recursive = recursive, maxDepth = maxDepth, maxPages = maxPages)

fun aDependency(
    group: String = "org.springframework.boot",
    artifact: String = "spring-boot",
    version: String = "4.0.0",
) = Dependency(group = group, artifact = artifact, version = version)
