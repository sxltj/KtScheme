package org.ltj.ktscm.core

import org.ltj.ktscm.parse.ASTNode
import org.ltj.ktscm.parse.MetaInfo

@Target(AnnotationTarget.FUNCTION)
annotation class ScmProcedure

@Target(AnnotationTarget.FUNCTION)
annotation class ScmSyntax

sealed class BuiltInFunc {
    class ImmediateFunc(val fn: (Environment, MetaInfo, List<Any>) -> Any) : BuiltInFunc()
    class LazyFunc(val fn: (Environment, MetaInfo, List<ASTNode>) -> Any) : BuiltInFunc()
}