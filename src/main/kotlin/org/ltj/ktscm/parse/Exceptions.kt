package org.ltj.ktscm.parse

import org.ltj.ktscm.core.Environment

class SyntaxException(message: String, val metaInfo: MetaInfo) :
    Exception("$message, at ${metaInfo.line}:${metaInfo.column}") {
    companion object {
        fun tooMuchRightParen(metaInfo: MetaInfo) = SyntaxException("Too much ')'", metaInfo)
        fun missingRightParen(metaInfo: MetaInfo) = SyntaxException("Missing ')'", metaInfo)
        fun invalidSymbolName(name: String, metaInfo: MetaInfo) =
            SyntaxException("Invalid symbol name: $name", metaInfo)

        fun illegalCharacter(char: Char, metaInfo: MetaInfo) = SyntaxException("Illegal character: $char", metaInfo)
        fun invalidSharpPrefix(prefix: Char, metaInfo: MetaInfo) =
            SyntaxException("Invalid sharp prefix: $prefix", metaInfo)

        fun incompleteSharpSign(str: String, metaInfo: MetaInfo) =
            SyntaxException("Invalid number start with zero: $str", metaInfo)

        fun incompleteQuote(metaInfo: MetaInfo) = SyntaxException("Incomplete quote", metaInfo)
        fun illegalStringLiteral(string: String, metaInfo: MetaInfo) =
            SyntaxException("Illegal string literal: $string", metaInfo)

        fun unexpectedDot(metaInfo: MetaInfo) = SyntaxException("Unexpected dot", metaInfo)
        fun illegalParamList(metaInfo: MetaInfo) = SyntaxException("Illegal param list", metaInfo)
        fun invalidNumber(num: String, metaInfo: MetaInfo) = SyntaxException("Invalid number: $num", metaInfo)
    }
}

class ScmRuntimeException(
    val msg: String,
    val metaInfo: MetaInfo?,
    val env: Environment?,
    val causedBy: MutableList<Pair<MetaInfo, Environment>>
) :
    Exception() {

    override val message: String?
        get() = "$msg, at ${if (metaInfo != null) "${metaInfo.line}:${metaInfo.column}" else "BUILT-IN"}" +
                if (causedBy.isNotEmpty()) "\n\t${causedBy.map { it.first }.joinToString("\n\t") {
                    "at ${it.line}:${it.column}${if (it.path == null) "" else ", ${it.path}"}"
                }}"
                else ""

    companion object {
        fun unboundVariable(
            variable: String,
            env: Environment? = null,
            metaInfo: MetaInfo? = null,
            causedBy: MutableList<Pair<MetaInfo, Environment>> = mutableListOf()
        ) =
            ScmRuntimeException("Unbound variable '$variable'", metaInfo, env, causedBy)

        fun tryingToApplyNonProcedure(
            name: String,
            env: Environment? = null,
            metaInfo: MetaInfo? = null,
            causedBy: MutableList<Pair<MetaInfo, Environment>> = mutableListOf()
        ) =
            ScmRuntimeException("Cannot apply non procedure '$name'", metaInfo, env, causedBy)

        fun missingArgs(
            required: Int,
            actual: Int,
            env: Environment? = null,
            funcName: String? = null,
            metaInfo: MetaInfo? = null,
            causedBy: MutableList<Pair<MetaInfo, Environment>> = mutableListOf()
        ) =
            ScmRuntimeException(
                "Missing args${if (funcName != null) "for '$funcName'" else ""}, required $required, actual $actual",
                metaInfo,
                env,
                causedBy
            )

        fun missingArgsForDynamic(
            atLeast: Int,
            actual: Int,
            env: Environment? = null,
            funcName: String? = null,
            metaInfo: MetaInfo? = null,
            causedBy: MutableList<Pair<MetaInfo, Environment>> = mutableListOf()
        ) = ScmRuntimeException(
            "Missing args${if (funcName != null) "for '$funcName'" else ""}, atLeast $atLeast, actual $actual",
            metaInfo,
            env,
            causedBy
        )

        fun tooMuchArgs(
            required: Int,
            actual: Int,
            env: Environment? = null,
            funcName: String? = null,
            metaInfo: MetaInfo? = null,
            causedBy: MutableList<Pair<MetaInfo, Environment>> = mutableListOf()
        ) =
            ScmRuntimeException(
                "Too much args${if (funcName != null) "for '$funcName'" else ""}, required $required, actual $actual",
                metaInfo,
                env,
                causedBy
            )

        fun invalidSyntax(
            env: Environment? = null,
            metaInfo: MetaInfo? = null,
            causedBy: MutableList<Pair<MetaInfo, Environment>> = mutableListOf()
        ) = ScmRuntimeException("Invalid syntax", metaInfo, env, causedBy)

        fun typeDismatch(
            type: ScmType,
            funcName: String,
            env: Environment? = null,
            metaInfo: MetaInfo? = null,
            causedBy: MutableList<Pair<MetaInfo, Environment>> = mutableListOf()
        ) = ScmRuntimeException("Type '${type.typeStr}' dismatch for '$funcName'", metaInfo, env, causedBy)
    }
}