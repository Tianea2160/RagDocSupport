package org.tianea.ragdocsupport.core.port

import org.tianea.ragdocsupport.core.model.DocSource

interface DocSourceRepository {
    fun findByLibrary(library: String): DocSource?
    fun findAll(): List<DocSource>
}
