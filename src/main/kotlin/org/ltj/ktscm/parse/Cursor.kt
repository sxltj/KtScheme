package org.ltj.ktscm.parse

internal data class Cursor(var line: Int, var column: Int, var index: Int) {

    fun forward(count: Int) {
        column += count
        index += count
    }

    fun set(another: Cursor) {
        line = another.line
        column = another.column
        index = another.index
    }

    fun newLine() {
        line++
        column = 1
        index++
    }
}