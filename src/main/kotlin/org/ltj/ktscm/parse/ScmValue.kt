package org.ltj.ktscm.parse

import org.ltj.ktscm.core.Environment
import org.ltj.ktscm.util.gcd
import org.ltj.ktscm.util.lcm
import kotlin.properties.Delegates

typealias ScmVector<E> = MutableList<E>

fun Any.force(): Any {
    return if (this is ScmValue.DelayedObject) {
        delayedOperation.eval(env)
    } else {
        this
    }
}

sealed class ScmValue {

    companion object {
        fun makeList(elements: List<Any>): ScmValue {
            return if (elements.isEmpty()) Nil else Cons(
                elements.first(),
                makeList(elements.subList(1, elements.size))
            )
        }

        fun makeListRecursively(elements: List<Any>): ScmValue {
            return if (elements.isEmpty()) {
                Nil
            } else {
                val first = elements.first()
                Cons(
                    if (first is ASTNode.ListNode) makeListRecursively(first.clauses as List<Any>) else first,
                    makeListRecursively(elements.subList(1, elements.size))
                )
            }
        }
    }

    data class Fraction(var numerator: Int, var denominator: Int) : ScmValue() {
        init {
            val gcd = gcd(numerator, denominator)
            numerator /= gcd
            denominator /= gcd
        }

        operator fun plus(any: Any): Any {
            var other by Delegates.notNull<Fraction>()
            when (any) {
                is Fraction -> other = any
                is Int -> Fraction(any, 1)
                is Double -> return numerator / denominator + any
                is Complex -> return any + this
            }
            val lcm = lcm(denominator, other.denominator)
            val m1 = denominator / lcm
            val m2 = other.denominator / lcm
            return Fraction(
                numerator * m1 + other.numerator * m2,
                lcm
            )
        }

        operator fun minus(other: Fraction): Any {
            throw NotImplementedError()
        }
    }

    data class Complex(var realPart: Any, var imagPart: Any) : ScmValue() {
        operator fun plus(any: Any): Complex {
            TODO("NOT IMPLEMENTED")
        }
    }

    data class Symbol(var name: String) : ScmValue()

    data class Closure(var param: Param, var body: Root, var env: Environment) {

        sealed class Param {
            data class FixedParams(val params: List<String>) : Param() {
                override fun pushArgs(args: List<Any>, env: Environment) {
                    args.forEachIndexed { i, a ->
                        env.defineVariable(params[i], a)
                    }
                }

                override fun checkArgCount(argCount: Int, env: Environment, metaInfo: MetaInfo) {
                    if (argCount < params.size) throw ScmRuntimeException.missingArgs(
                        params.size,
                        argCount,
                        env,
                        null,
                        metaInfo
                    )
                    else if (argCount > params.size) throw ScmRuntimeException.tooMuchArgs(
                        params.size,
                        argCount,
                        env,
                        null,
                        metaInfo
                    )
                }
            }

            data class DynamicParams(val fixedPart: List<String>?, val dynamicPart: String) : Param() {
                override fun pushArgs(args: List<Any>, env: Environment) {
                    if (fixedPart != null) {
                        for (i in 0..fixedPart.size) {
                            env.defineVariable(fixedPart[i], args[i])
                        }
                        env.defineVariable(dynamicPart, ScmValue.makeList(args.subList(fixedPart.size, args.size)))
                    } else {
                        env.defineVariable(dynamicPart, ScmValue.makeList(args))
                    }
                }

                override fun checkArgCount(argCount: Int, env: Environment, metaInfo: MetaInfo) {
                    if (fixedPart != null && argCount < fixedPart.size) throw ScmRuntimeException.missingArgsForDynamic(
                        fixedPart.size,
                        argCount,
                        env,
                        null,
                        metaInfo
                    )
                }
            }

            abstract fun checkArgCount(argCount: Int, env: Environment, metaInfo: MetaInfo)

            // Let's assume we checked the arg count before push them to the closure
            abstract fun pushArgs(args: List<Any>, env: Environment)
        }

        fun apply(args: List<Any>, metaInfo: MetaInfo): Any {
            val localEnv = Environment.createEnv(env)
            param.checkArgCount(args.size, localEnv, metaInfo)
            param.pushArgs(args, localEnv)
            return body.eval(localEnv)
        }
    }

    object NaN : ScmValue()

    object Infinite : ScmValue()

    data class Cons(var head: Any, var tail: Any) : ScmValue()

    object Nil : ScmValue() {
        override fun toString(): String {
            return "Nil"
        }
    }

    data class DelayedObject(var delayedOperation: ASTNode, var env: Environment) : ScmValue()

    data class Root(var clauses: List<ASTNode>) {
        fun eval(env: Environment): Any {
            clauses.forEachIndexed { index, astNode ->
                if (index != clauses.size - 1) {
                    astNode.eval(env)
                } else {
                    return astNode.eval(env)
                }
            }
            throw NotImplementedError()
        }
    }

    data class SyntaxRules(
        var keywords: List<String>,
        var bindings: List<SyntaxRule>,
        var env: Environment
    ) : ScmValue() {

        data class SyntaxRule(var bindings: List<SyntaxBinding>, var body: Root)

        sealed class SyntaxBinding {
            data class AtomBinding(var name: String) : SyntaxBinding()
            data class VarargBinding(var name: String) : SyntaxBinding()
            data class KeywordBinding(var keyword: String) : SyntaxBinding()
            data class ListBinding(var clauses: List<SyntaxBinding>) : SyntaxBinding()
        }

        private fun matchList(bindings: List<SyntaxBinding>, args: List<ASTNode>): Boolean {
            if (bindings.count { it is SyntaxBinding.AtomBinding || it is SyntaxBinding.KeywordBinding || it is SyntaxBinding.ListBinding } > args.size)
                return false

            bindings.forEachIndexed { i, binding ->
                when (binding) {
//                    is SyntaxBinding.AtomBinding -> {
//                        if (args[i] !is ASTNode.LiteralNode && args[i] !is ASTNode.SymbolNode) {
//                            return false
//                        }
//                    }
                    is SyntaxBinding.KeywordBinding -> {
                        if (args[i] !is ASTNode.SymbolNode || args[i] !is ASTNode.SpecialNode) {
                            return false
                        }
                    }
                    is SyntaxBinding.ListBinding -> {
                        if (args[i] !is ASTNode.ListNode) {
                            return false
                        } else {
                            if (!matchList(binding.clauses, (args[i] as ASTNode.ListNode).clauses)) {
                                return false
                            }
                        }
                    }
                }
            }
            return true
        }

        private fun pushArgs(bindings: List<SyntaxBinding>, args: List<ASTNode>, outerEnv: Environment) {
            bindings.forEachIndexed { i, binding ->
                when (binding) {
                    is SyntaxBinding.ListBinding -> pushArgs(
                        binding.clauses,
                        (args[i] as ASTNode.ListNode).clauses,
                        outerEnv
                    )
                    is SyntaxBinding.AtomBinding -> env.defineVariable(
                        binding.name,
                        DelayedObject(args[i], outerEnv)
                    )
                    is SyntaxBinding.VarargBinding -> env.defineVariable(
                        "${binding.name} ...",
                        args.subList(i, args.size)
                    )
                }
            }
        }

        fun match(args: List<ASTNode>, outerEnv: Environment, metaInfo: MetaInfo): Any {
            val matchedBindings = bindings.filter {
                matchList(it.bindings, args)
            }

            if (matchedBindings.isEmpty()) {
                // Match failed
                throw ScmRuntimeException.invalidSyntax(env, metaInfo)
            }

            pushArgs(matchedBindings.first().bindings, args, outerEnv)

            val body = matchedBindings.first().body
            return body.eval(env)
        }
    }
}