package org.ltj.ktscm.parse

import java.lang.StringBuilder

enum class TokenType {
    NUMBER,
    CHAR,
    STRING,
    BOOLEAN,
    SYMBOL,
    DOT,
    SHARP
}

enum class QuoteType {
    LIST, VECTOR
}

private fun readNumber(string: String, meta: MetaInfo, radix: Int): ASTNode.LiteralNode.NumberNode {
    return when {
        string.contains('i') -> {
            TODO("NOT IMP")
        }
        string.contains('/') -> ASTNode.LiteralNode.NumberNode(
            ScmValue.Fraction(
                string.substring(0, string.indexOf('/')).toInt(radix),
                string.substring(string.indexOf('/') + 1).toInt(radix)
            ), meta
        )
        string.contains('.') -> if (radix == 10) {
            ASTNode.LiteralNode.NumberNode(string.toDouble(), meta)
        } else {
            val index = string.indexOf('.')
            val part2Str = string.substring(index + 1)
            val part1 = string.substring(0, index).toInt(radix)
            val part2 = part2Str.toInt(radix) / ("1".padEnd(part2Str.length, '0').toInt(radix))
            ASTNode.LiteralNode.NumberNode(part1 + part2, meta)
        }
        else -> ASTNode.LiteralNode.NumberNode(string.toInt(radix), meta)
    }
}

private fun readNumber(string: String, meta: MetaInfo): ASTNode.LiteralNode.NumberNode {
    val completed = when {
        string.startsWith(".") -> "0$string"
        string.startsWith("#") && string.substring(2).startsWith('.') -> StringBuilder(string).insert(2, '0').toString()
        string.endsWith(".") -> "${string}0"
        else -> string
    }
    return when {
        string.startsWith("#b") -> readNumber(completed.substring(2), meta, 2)
        string.startsWith("#o") -> readNumber(completed.substring(2), meta, 8)
        string.startsWith("#d") -> readNumber(completed.substring(2), meta, 10)
        string.startsWith("#x") -> readNumber(completed.substring(2), meta, 16)
        else -> readNumber(completed, meta, 10)
    }
}

sealed class Token {

    abstract fun toAST(): ASTNode

    data class Atom(var kind: TokenType, var content: String, var meta: MetaInfo) : Token() {
        override fun toAST(): ASTNode {
            return when (kind) {
                TokenType.NUMBER -> readNumber(content, meta)
                TokenType.STRING -> ASTNode.LiteralNode.StringNode(content, meta)
                TokenType.SYMBOL -> ASTNode.SymbolNode(content, meta)
                TokenType.CHAR -> ASTNode.LiteralNode.CharNode(content[0], meta)
                TokenType.BOOLEAN -> ASTNode.LiteralNode.BooleanNode(content == "#t", meta)
                TokenType.DOT -> ASTNode.SpecialNode(ASTNode.SpecialNodeType.DOT, meta)
                TokenType.SHARP -> ASTNode.SpecialNode(ASTNode.SpecialNodeType.SHARP, meta)
            }
        }
    }

    data class List(var clauses: kotlin.collections.List<Token>, var meta: MetaInfo) : Token() {
        override fun toAST(): ASTNode {
            return ASTNode.ListNode(clauses.map { it.toAST() }, meta)
        }
    }

    data class Root(var clauses: kotlin.collections.List<Token>) : Token() {
        override fun toAST(): ASTNode {
            return ASTNode.RootNode(ScmValue.Root(clauses.map { it.toAST() }))
        }
    }

    data class Quote(var type: QuoteType, var content: Token, var meta: MetaInfo) : Token() {
        override fun toAST(): ASTNode {
            return ASTNode.LiteralNode.QuoteNode(content.toAST(), meta)
        }
    }

    // data class QuasiQuote(var type: QuoteType, var content: )
}