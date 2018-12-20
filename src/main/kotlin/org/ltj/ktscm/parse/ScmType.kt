package org.ltj.ktscm.parse

sealed class ScmType(val typeStr: kotlin.String) {

    companion object {
        fun isNumber(scmType: ScmType): kotlin.Boolean {
            return scmType is Int ||
                    scmType is Fraction ||
                    scmType is Float ||
                    scmType is Infinite
        }

        fun isPair(scmType: ScmType): kotlin.Boolean {
            return scmType is Cons
        }
    }

    object String : ScmType("String")
    object Char : ScmType("Char")
    object Boolean : ScmType("Boolean")
    object NaN : ScmType("NaN")
    object Int : ScmType("Int")
    object Fraction : ScmType("Fraction")
    object Float : ScmType("Float")
    object Infinite : ScmType("Inf")
    object Symbol : ScmType("Symbol")
    object Cons : ScmType("Pair")
    object Nil : ScmType("Nil")
    object Closure : ScmType("Procedure")
    object Vector : ScmType("Vector")
}

fun Any.getType(): ScmType {
    return when(this) {
        is kotlin.String -> ScmType.String
        is kotlin.Char -> ScmType.Char
        is kotlin.Int -> ScmType.Int
        is kotlin.Double -> ScmType.Float
        is kotlin.Boolean -> ScmType.Boolean
        is ScmValue.Fraction -> ScmType.Fraction
        is ScmValue.NaN -> ScmType.NaN
        is ScmValue.Infinite -> ScmType.Infinite
        is ScmValue.Symbol -> ScmType.Symbol
        is ScmValue.Cons -> ScmType.Cons
        is ScmValue.Nil -> ScmType.Nil
        is ScmValue.Closure -> ScmType.Closure
        is ScmVector<*> -> ScmType.Vector
        else -> throw UnsupportedOperationException()
    }
}