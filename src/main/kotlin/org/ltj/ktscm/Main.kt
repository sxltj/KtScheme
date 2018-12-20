package org.ltj.ktscm

import org.ltj.ktscm.core.Environment
import org.ltj.ktscm.parse.Scanner
import org.ltj.ktscm.parse.Token

fun main(args: Array<String>) {
    val scanner = Scanner(Token::class.java.classLoader.getResourceAsStream("test.scm"), "test.scm")
    println(scanner.scan().toAST().eval(Environment.DEFAULT_ENV))
}