package org.ltj.ktscm.parse

import java.io.File
import java.io.InputStream
import java.lang.StringBuilder
import java.nio.charset.Charset

class Scanner(private val source: String, private val fileName: String? = null) {

    constructor(
        ins: InputStream,
        fileName: String? = null
    ) : this(ins.readAllBytes().toString(Charset.defaultCharset()), fileName)

    constructor(file: File) : this(file.readText(Charset.defaultCharset()), file.name)

    companion object {
        private const val BLANK = " \n\t\r\u000C"
        private const val LEFT_PAREN = "(["
        private const val RIGHT_PAREN = ")]"
        private const val SPLITTER = RIGHT_PAREN + LEFT_PAREN + BLANK
        private const val DECIMAL_NUM = "0123456789"
        private const val DECIMAL = DECIMAL_NUM + ".-/"
        private const val BINARY = ".-/01"
        private const val OCTAL = ".-/01234567"
        private const val HEXADECIMAL = DECIMAL + "abcdefABCDEF"
        private const val DC_LETTERS = "abcdefghijklmnopqrstuvwxyz"
        private const val UC_LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private const val LETTERS = DC_LETTERS + UC_LETTERS
        private const val SPECIAL = "!@$%^&*_-+=:'/?.>,<"
        private const val VALID_SYMBOL = LETTERS + SPECIAL + DECIMAL_NUM
        private const val QUOTE_START = "`'"
    }

    private var cursor = Cursor(1, 1, 0)
    private var roundParenCount = 0
    private var squareParenCount = 0

    fun scan(): Token.Root {
        val currentNodeList = mutableListOf<Token>()
        while (hasNext()) {
            readNode()?.let { currentNodeList.add(it) }
        }
        if (roundParenCount > 0 || squareParenCount > 0) {
            throw SyntaxException.missingRightParen(MetaInfo(cursor, fileName))
        }
        return Token.Root(currentNodeList)
    }

    private fun readNode(): Token? {
        return when (peek()) {
            '\n' -> {
                cursor.newLine()
                readNode()
            }
            in BLANK -> {
                cursor.forward(1)
                readNode()
            }
            '(' -> {
                roundParenCount++
                readCombine()
            }
            '[' -> {
                squareParenCount++
                readCombine()
            }
            ')' -> {
                checkRightParenCount(roundParenCount)
                roundParenCount--
                null
            }
            ']' -> {
                checkRightParenCount(squareParenCount)
                squareParenCount--
                null
            }
            '.' -> readDot()
            in DECIMAL -> readNumber()
            '"' -> readString()
            '#' -> readSharp()
            in QUOTE_START -> readQuote()
            ';' -> skipComment()
            in VALID_SYMBOL -> readSymbol()
            else -> throw SyntaxException.illegalCharacter(
                peek(), MetaInfo(cursor, fileName)
            )
        }
    }

    private fun readDot(): Token? {
        if (hasNext(1)) {
            return when (peek(1)) {
                in DECIMAL_NUM -> readNumber()
                in VALID_SYMBOL -> readSymbol()
                in SPLITTER -> {
                    cursor.forward(1)
                    Token.Atom(TokenType.DOT, ".", MetaInfo(cursor.column - 1, cursor.line, fileName))
                }
                else -> throw SyntaxException.invalidSymbolName(
                    subStringToSplitter(cursor.index), MetaInfo(cursor, fileName)
                )
            }
        } else {
            throw SyntaxException.illegalCharacter('.', MetaInfo(cursor, fileName))
        }
    }

    private fun skipComment(): Token? {
        do {
            cursor.forward(1)
        } while (hasNext() && peek() != '\n')

        return if (hasNext()) {
            readNode()
        } else {
            null
        }
    }

    private fun readCombine(): Token.List {
        val nodes = mutableListOf<Token>()
        val mark = MetaInfo(cursor, fileName)
        cursor.forward(1)
        while (hasNext()) {
            val node = readNode()
            if (node != null) {
                nodes.add(node)
            } else {
                break
            }
        }
        return Token.List(nodes, mark)
    }

    private fun readQuote(): Token.Quote {
        val mark = MetaInfo(cursor, fileName)
        cursor.forward(1)
        if (hasNext()) {
            val next = readNode()
            if (next is Token.Atom && next.kind == TokenType.SHARP) {
                // We get a vector quote
                if (hasNext()) {
                    return Token.Quote(
                        QuoteType.VECTOR,
                        readNode() ?: throw SyntaxException.incompleteQuote(mark),
                        mark
                    )
                } else {
                    throw SyntaxException.incompleteQuote(mark)
                }
            } else {
                return Token.Quote(
                    QuoteType.LIST,
                    next ?: throw SyntaxException.incompleteQuote(mark),
                    mark
                )
            }
        } else {
            throw SyntaxException.incompleteQuote(mark)
        }
    }

    private fun readString(): Token.Atom {
        val mark = MetaInfo(cursor, fileName)
        val string = StringBuilder()
        var escaping = false
        var legalString = true
        loop@ do {
            cursor.forward(1)
            val char = peek()
            when {
                char == '"' && !escaping -> break@loop
                char == '\\' && !escaping -> escaping = true
                escaping -> if (char == '\\' || char == '"') {
                    escaping = false
                    string.append(char)
                } else {
                    legalString = false
                }
                else -> string.append(char)
            }
        } while (hasNext())

        if (!legalString) {
            throw SyntaxException.illegalStringLiteral(string.toString(), mark)
        }

        cursor.forward(1)
        if (hasNext() && peek() !in SPLITTER) {
            throw SyntaxException.illegalCharacter(peek(), MetaInfo(cursor, fileName))
        }

        return Token.Atom(TokenType.STRING, string.toString(), mark)
    }

    private fun readNumber(
        legalNumbers: String,
        hasSharpPrefix: Boolean = true
    ): Token.Atom {
        val mark = cursor.copy()
        if (hasSharpPrefix) {
            cursor.forward(2)
        }

        while (hasNext() && peek() in legalNumbers) {
            cursor.forward(1)
        }

        val num = source.substring(mark.index, cursor.index)

        val iCount = num.count { it == 'i' }
        val pmCount = num.count { it == '+' || it == '-' }
        val validNumber = num.count { it == '-' } < 2
                && num.count { it == '.' } < 2
                && num.count { it == '/' } < 2
                && (!num.contains('.') || !num.contains('/'))
                && num.count { it == '/' } < 2
                && num.last() != '/'
                && num.first() != '/'
                && iCount < 2
                && pmCount < 2
                && if (iCount != 0) iCount + pmCount == 2 else iCount + pmCount == 0
        if (hasNext()) {
            val end = peek()
            if ((end in VALID_SYMBOL || !validNumber) && !hasSharpPrefix) {
                cursor.set(mark)
                return readSymbol()
            } else if (end !in SPLITTER || !validNumber) {
                throw SyntaxException.invalidNumber(
                    subStringToSplitter(mark.index), MetaInfo(cursor, fileName)
                )
            }
        }

        return Token.Atom(
            TokenType.NUMBER, num,
            MetaInfo(mark, fileName)
        )
    }

    private fun readNumber(): Token.Atom {
        val begin = cursor.copy()
        if (peek() == '#') {
            if (!hasNext(2) || peek(2) in SPLITTER) {
                throw SyntaxException.incompleteSharpSign(
                    source.substring(cursor.index, cursor.index + 2),
                    MetaInfo(cursor, fileName)
                )
            }

            return when (source[begin.index + 1]) {
                'b' -> readNumber(BINARY)
                'o' -> readNumber(OCTAL)
                'd' -> readNumber(DECIMAL)
                'x' -> readNumber(HEXADECIMAL)
                else -> throw UnsupportedOperationException()
            }
        } else {
            return readNumber(DECIMAL, false)
        }
    }

    private fun readBoolean(): Token.Atom {
        val token = Token.Atom(
            TokenType.BOOLEAN,
            source.substring(cursor.index, cursor.index + 2), MetaInfo(cursor)
        )
        cursor.forward(2)

        if (hasNext() && peek() !in SPLITTER) {
            throw SyntaxException.invalidSymbolName(
                subStringToSplitter(cursor.index - 2), MetaInfo(cursor)
            )
        }

        return token
    }

    private fun readChar(): Token.Atom {
        if (hasNext(2)) {
            throw SyntaxException.invalidSymbolName(
                subStringToSplitter(cursor.index), MetaInfo(cursor.line, cursor.column, fileName)
            )
        }
        if (hasNext(3)) {
            val outerBorder = peek(3)
            if (outerBorder !in SPLITTER) {
                throw SyntaxException.illegalCharacter(
                    outerBorder, MetaInfo(cursor.line, cursor.column + 3, fileName)
                )
            }
        }

        val token = Token.Atom(
            TokenType.CHAR,
            peek(2).toString(),
            MetaInfo(cursor, fileName)
        )
        cursor.forward(3)
        return token
    }

    private fun readSharp(): Token.Atom {
        return when (peek(1)) {
            in "tf" -> readBoolean()
            '\\' -> readChar()
            in "boxd" -> readNumber()
            else -> {
                if (peek(1) !in LEFT_PAREN) {
                    throw SyntaxException.invalidSharpPrefix(
                        peek(1), MetaInfo(cursor, fileName)
                    )
                } else {
                    Token.Atom(TokenType.SHARP, "#", MetaInfo(cursor, fileName))
                }
            }
        }
    }

    private fun readSymbol(): Token.Atom {
        val mark = cursor.copy()
        while (hasNext() && peek() in VALID_SYMBOL) {
            cursor.forward(1)
        }

        if (hasNext()) {
            val end = peek()
            if (end !in SPLITTER) {
                throw SyntaxException.invalidSymbolName(
                    subStringToSplitter(mark.index),
                    MetaInfo(mark)
                )
            }
        }

        val symbol = source.substring(mark.index, cursor.index)
        return Token.Atom(TokenType.SYMBOL, symbol, MetaInfo(mark, fileName))
    }

    private fun hasNext(advance: Int = 0) = cursor.index + advance < source.length

    private fun peek(advance: Int = 0) = source[cursor.index + advance]

    private fun subStringToSplitter(subStrStart: Int, findStrStart: Int = cursor.index): String {
        val tmp = source.indexOfAny(SPLITTER.toCharArray(), findStrStart)
        return source.substring(subStrStart, if (tmp == -1) source.length else tmp)
    }

    private fun checkRightParenCount(parenCount: Int) {
        if (parenCount == 0) {
            throw SyntaxException.tooMuchRightParen(MetaInfo(cursor, fileName))
        } else {
            cursor.forward(1)
        }
    }
}