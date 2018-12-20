package org.ltj.ktscm.parse

import org.ltj.ktscm.core.Environment
import org.ltj.ktscm.core.BuiltInFunc

sealed class ASTNode {

    abstract fun eval(env: Environment): Any

    sealed class LiteralNode(var value: Any, var metaInfo: MetaInfo) : ASTNode() {

        override fun eval(env: Environment): Any {
            return value
        }

        class StringNode(value: String, metaInfo: MetaInfo) :
            LiteralNode(value, metaInfo)

        class CharNode(value: Char, metaInfo: MetaInfo) :
            LiteralNode(value, metaInfo)

        class BooleanNode(value: Boolean, metaInfo: MetaInfo) :
            LiteralNode(value, metaInfo)

        class QuoteNode(value: ASTNode, metaInfo: MetaInfo) :
            LiteralNode(value, metaInfo) {
            override fun eval(env: Environment): Any {
                return when (value) {
                    is ASTNode.SpecialNode -> throw SyntaxException.illegalCharacter(
                        '.',
                        (value as SpecialNode).metaInfo
                    )
                    is ASTNode.SymbolNode -> ScmValue.Symbol((value as SymbolNode).value)
                    is ASTNode.LiteralNode -> (value as LiteralNode).eval(env)
                    is ASTNode.ListNode -> ScmValue.makeListRecursively(
                        (value as ListNode).clauses as List<Any>
                    )
                    else -> throw NotImplementedError()
                }
            }
        }

        class NumberNode(value: Any, metaInfo: MetaInfo) :
            LiteralNode(value, metaInfo)
    }

    data class SymbolNode(var value: String, var metaInfo: MetaInfo) : ASTNode() {
        override fun eval(env: Environment): Any {
            return env.get(value) ?: throw ScmRuntimeException.unboundVariable(value, env, metaInfo)
        }
    }

    data class ListNode(var clauses: List<ASTNode>, var metaInfo: MetaInfo) : ASTNode() {
        override fun eval(env: Environment): Any {
            val first = clauses.first().eval(env)
            return when (first) {
                is ScmValue.Closure, is BuiltInFunc -> env.applyFunction(
                    first,
                    clauses.subList(1, clauses.size),
                    metaInfo
                )
                is ScmValue.SyntaxRules -> first.match(
                    clauses.subList(1, clauses.size),
                    env,
                    metaInfo
                )
                else -> throw ScmRuntimeException.tryingToApplyNonProcedure(first.toString(), env, metaInfo)
            }
        }
    }

    data class RootNode(var root: ScmValue.Root) : ASTNode() {
        override fun eval(env: Environment): Any {
            return root.eval(env)
        }
    }

    data class SpecialNode(var clauses: SpecialNodeType, var metaInfo: MetaInfo) : ASTNode() {
        override fun eval(env: Environment) {}
    }

    enum class SpecialNodeType {
        DOT, SHARP
    }
}