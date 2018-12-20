package org.ltj.ktscm.core

import org.ltj.ktscm.core.BuiltInFunc.*
import org.ltj.ktscm.parse.*
import kotlin.properties.Delegates
import org.ltj.ktscm.parse.ScmType
import org.ltj.ktscm.parse.ScmType.Companion.isNumber
import org.ltj.ktscm.parse.ScmType.Companion.isPair

private fun requireArgs(
    funcName: String,
    args: List<Any>,
    expectedCount: Int
) {
    if (args.size < expectedCount) throw ScmRuntimeException.missingArgs(expectedCount, args.size, funcName = funcName)
    if (args.size > expectedCount) throw ScmRuntimeException.tooMuchArgs(expectedCount, args.size, funcName = funcName)
}

private fun requireArgsForDynamic(funcName: String, args: List<ASTNode>, least: Int) {
    if (args.size < least) throw ScmRuntimeException.missingArgsForDynamic(least, args.size, funcName = funcName)
}

private fun requireArgsType(funcName: String, args: List<Any>, vararg expectedTypesPredicate: (ScmType) -> Boolean) {
    args.forEachIndexed { i, arg ->
        var type by Delegates.notNull<(ScmType) -> Boolean>()
        type = if (i >= expectedTypesPredicate.size) {
            expectedTypesPredicate.last()
        } else {
            expectedTypesPredicate[i]
        }
        if (!type(arg.getType())) {
            throw ScmRuntimeException.typeDismatch(arg.getType(), funcName)
        }
    }
}

private fun parseParamList(params: ASTNode): ScmValue.Closure.Param {
    when (params) {
        is ASTNode.ListNode -> {
            var dot = false
            val fixedParamList = mutableListOf<String>()
            var dynamicParam: String? = null
            params.clauses.forEach {
                when (it) {
                    is ASTNode.SpecialNode -> {
                        if (!dot) dot = true
                        else throw SyntaxException.unexpectedDot(it.metaInfo)
                    }
                    is ASTNode.LiteralNode -> {
                        throw SyntaxException.illegalParamList(params.metaInfo)
                    }
                    is ASTNode.SymbolNode -> {
                        if (!dot) fixedParamList.add(it.value)
                        else dynamicParam = it.value
                    }
                }
            }

            if ((fixedParamList.isEmpty() && dot) || (dot && dynamicParam == null)) {
                throw SyntaxException.illegalParamList(params.metaInfo)
            }

            return if (dot) {
                ScmValue.Closure.Param.DynamicParams(fixedParamList, dynamicParam!!)
            } else {
                ScmValue.Closure.Param.FixedParams(fixedParamList)
            }
        }
        is ASTNode.SymbolNode -> // Dynamic param
            return ScmValue.Closure.Param.DynamicParams(null, params.value)
        is ASTNode.LiteralNode -> throw SyntaxException.illegalParamList(params.metaInfo)
        is ASTNode.ListNode -> throw SyntaxException.illegalParamList(params.metaInfo)
        is ASTNode.SpecialNode -> throw SyntaxException.unexpectedDot(params.metaInfo)
        else -> throw NotImplementedError()
    }
}

@ScmSyntax
private fun lambda(env: Environment, metaInfo: MetaInfo, args: List<ASTNode>): Any {
    requireArgsForDynamic("lambda", args, 2)
    val param = parseParamList(args.first())
    val body = ScmValue.Root(args.subList(1, args.size))
    return ScmValue.Closure(param, body, env)
}

@ScmProcedure
private fun cons(env: Environment, metaInfo: MetaInfo, args: List<Any>): Any {
    requireArgs("cons", args, 2)
    return ScmValue.Cons(args.first(), args.last())
}

@ScmProcedure
private fun car(env: Environment, metaInfo: MetaInfo, args: List<Any>): Any {
    requireArgs("car", args, 1)
    requireArgsType("car", args, ::isPair)
    return (args.first() as ScmValue.Cons).head
}

@ScmProcedure
private fun cdr(env: Environment, metaInfo: MetaInfo, args: List<Any>): Any {
    requireArgs("cdr", args, 1)
    requireArgsType("cdr", args, ::isPair)
    return (args.first() as ScmValue.Cons).tail
}

@ScmProcedure
private fun plus(env: Environment, metaInfo: MetaInfo, args: List<Any>): Any {
    requireArgsType("+", args, ::isNumber)
    return args.fold(0) { accu, arg ->
        accu + (arg as Int)
    }
}

@ScmProcedure
private fun load(env: Environment, metaInfo: MetaInfo, args: List<Any>): Any {
    throw NotImplementedError()
}

val R5RS_BUILTIN = mutableMapOf<String, Any>(
    Pair("lambda", LazyFunc(::lambda)),
    Pair("cons", ImmediateFunc(::cons)),
    Pair("car", ImmediateFunc(::car)),
    Pair("cdr", ImmediateFunc(::cdr)),
    Pair("+", ImmediateFunc(::plus)),
    Pair("load", ImmediateFunc(::load))
)