package org.ltj.ktscm.util

// Greatest common divisor
fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

// Least common multiple
fun lcm(a: Int, b: Int): Int = throw NotImplementedError()