// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package lambdas_kstyle_generics

private var COUNT = 11

private fun next() = "${COUNT++}"

data class Alpha(val id: String = next())

data class Beta(val id: String = next())

data class Gamma<out T>(val payload: T?, val id: String = next())

fun <T> consume(t: T, l: (t: T) -> String) = l(t)

fun main(args: Array<String>) {
    println(consume(Any(), { "${Alpha()}" }))
    println(consume(Any(), { "${Beta()}" }))
    println(consume(Any(), { "${Gamma("{any}")}" }))
    println(consume(Alpha(), { "$it" }))

    testFirst(11)
    testSecond(22)
    testThird()
}

private fun testFirst(sh: Short) {
    val prefix = "First"
    println(consume(Beta(), { "$prefix-1-$it" }))
    println(consume(Beta(), { "$prefix-2-$it" }))
    println(consume(Beta(), { "$prefix-3-$it" }))
    println(consume(Gamma(next()), { "$prefix-A-$it-$sh" }))
    println(consume(Gamma(next()), { "$prefix-B-$it-$sh" }))
    println(consume(Gamma(next()), { "$prefix-C-$it-$sh" }))
    println(consume(Gamma(COUNT++), { "$prefix-D-$it-$sh" }))
    println(consume(Gamma(COUNT++), { "$prefix-E-$it-$sh" }))
    println(consume(Gamma(COUNT++), { "$prefix-F-$it-$sh" }))
}

private fun testSecond(sh: Short) {
    val prefix = "Second"
    println(consume(Beta(), { "$prefix-1-$it" }))
    println(consume(Beta(), { "$prefix-2-$it" }))
    println(consume(Beta(), { "$prefix-3-$it" }))
    println(consume(Gamma(next()), { "$prefix-A-$it-$sh" }))
    println(consume(Gamma(next()), { "$prefix-B-$it-$sh" }))
    println(consume(Gamma(next()), { "$prefix-C-$it-$sh" }))
    println(consume(Gamma(COUNT++), { "$prefix-D-$it-$sh" }))
    println(consume(Gamma(COUNT++), { "$prefix-E-$it-$sh" }))
    println(consume(Gamma(COUNT++), { "$prefix-F-$it-$sh" }))
}

private fun testThird() {
    println(consume(4321, { "$it ${next()} ${next()} ${next()}" }))
    println(consume(1234, { "$it ${Alpha()} ${Beta()} ${Gamma(next())}" }))
}

