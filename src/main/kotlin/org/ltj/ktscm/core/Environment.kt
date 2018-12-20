package org.ltj.ktscm.core

import org.ltj.ktscm.parse.*

class Environment private constructor(
    // If parent environment is null, it's the top/global environment
    val parent: Environment?,
    // Stores use defined or built-in procedures, variables and syntax
    val frame: MutableMap<String, Any>
) {
    companion object {
        val DEFAULT_ENV: Environment = Environment(null, mutableMapOf())

        init {
            DEFAULT_ENV.defineVariable("PI", 3.1415926)
            DEFAULT_ENV.defineVariable("nil", ScmValue.Nil)
            DEFAULT_ENV.frame.putAll(R5RS_BUILTIN)
        }

        fun createEnv(parent: Environment?): Environment {
            return Environment(parent, mutableMapOf())
        }
    }

    fun defineFunction(name: String, param: ScmValue.Closure.Param, body: ScmValue.Root) {
        frame[name] = ScmValue.Closure(param, body, this)
    }

    fun defineVariable(name: String, value: Any) {
        frame[name] = value
    }

    fun get(name: String): Any? {
        return frame[name] ?: parent?.get(name)
    }

    private fun addStackTrace(e: ScmRuntimeException, metaInfo: MetaInfo) {
        if ((this != e.env && e.causedBy.isEmpty()) ||
            (e.causedBy.isNotEmpty() && this != e.causedBy.last().second)
        ) {
            e.causedBy.add(Pair(metaInfo, this))
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun applyFunction(func: Any, args: List<ASTNode>, metaInfo: MetaInfo): Any {
        when (func) {
            is ScmValue.Closure -> {
                try {
                    return func.apply(args.map {
                        try {
                            it.eval(this).force()
                        } catch (except: ScmRuntimeException) {
                            addStackTrace(except, metaInfo)
                            throw except
                        }
                    }, metaInfo)
                } catch (except: ScmRuntimeException) {
                    addStackTrace(except, metaInfo)
                    throw except
                }
            }
            is BuiltInFunc.LazyFunc -> {
                try {
                    return func.fn(this, metaInfo, args)
                } catch (e: ScmRuntimeException) {
                    addStackTrace(e, metaInfo)
                    throw e
                }
            }
            is BuiltInFunc.ImmediateFunc -> {
                return try {
                    func.fn(this, metaInfo, args.map {
                        try {
                            it.eval(this).force()
                        } catch (except: ScmRuntimeException) {
                            addStackTrace(except, metaInfo)
                            throw except
                        }
                    })
                } catch (e: ScmRuntimeException) {
                    addStackTrace(e, metaInfo)
                    throw e
                }
            }
        }

        throw NotImplementedError()
    }
}