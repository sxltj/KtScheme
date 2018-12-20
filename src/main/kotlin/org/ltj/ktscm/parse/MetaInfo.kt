package org.ltj.ktscm.parse

data class MetaInfo(var line: Int, var column: Int, var path: String? = null) {
    internal constructor(cursor: Cursor, path: String? = null) : this(cursor.line, cursor.column, path)
}