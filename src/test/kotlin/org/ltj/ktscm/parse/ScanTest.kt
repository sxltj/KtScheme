package org.ltj.ktscm.parse

import org.junit.Assert.*
import org.junit.Test
import org.ltj.ktscm.parse.Token.Atom

class ScanTest {

    @Test
    fun testScanQuote() {
        val source = "'a ''b ''('c 'b) ''''d"
        val scanner = Scanner(source)
        assertEquals(4, scanner.scan().clauses.size)
    }

    @Test
    fun testScanString() {
        val source = "\"\\\\str\\\\\\\"\""
        val scanner = Scanner(source)
        assertEquals("\\str\\\"", (scanner.scan().clauses[0] as Atom).content)
    }

    @Test
    fun testScanNumber() {
        val source = "#d1 1 #b100110 #o17645 #xFFFFFF"
        val scanner = Scanner(source)
        assertEquals(5, scanner.scan().clauses.size)
    }

    @Test
    fun testSkipComment() {
        val source = "; Comment\n;; More Comment\n#o2 ; End Line Comment"
        val scanner = Scanner(source)
        assertEquals("#o2", (scanner.scan().clauses[0] as Atom).content)
    }

    @Test
    fun testScanBoolean() {
        val source = "#t #f #t #f"
        val scanner = Scanner(source)
        assertEquals(4, scanner.scan().clauses.size)
    }
}