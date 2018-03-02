// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package lambdas.kstyle.trivial

import lambdas.kstyle.trivial.inner.testInner

private var COUNT = 11

private fun next() = "${COUNT++}"

fun consumeEmpty(l: () -> String) = l()
fun consumeOne(l: (x: String) -> String): String = l(next())
fun consumeTwo(l: (x: String, y: String) -> String): String = l(next(), next())
fun consumeThree(l: (x: String, y: String, z: String) -> String) = l(next(), next(), next())
fun consumeTwentyTwo(l: (v0: String, v1: String, v2: String, v3: String, v4: String,
                         v5: String, v6: String, v7: String, v8: String, v9: String,
                         v10: String, v11: String, v12: String, v13: String, v14: String,
                         v15: String, v16: String, v17: String, v18: String, v19: String,
                         v20: String, v21: String) -> String) = l(
        next(), next(), next(), next(), next(), next(), next(), next(), next(), next(), next(),
        next(), next(), next(), next(), next(), next(), next(), next(), next(), next(), next())

fun main(args: Array<String>) {
    test()
    testInner()
    testPrimitive()
    testUnit()
}

private fun test() {
    testStateless()
}

private fun testStateless() {
    println(consumeEmpty { "first empty" })
    println(consumeEmpty { "second empty" })

    println(consumeOne { _ -> "first single" })
    println(consumeOne { _ -> "second single" })
    println(consumeOne { _ -> "third single" })
    println(consumeOne { x ->
        try {
            throw RuntimeException("exception#$x")
        } catch (e: RuntimeException) {
            "caught: ${e.message}"
        } catch (e: Exception) {
            "NEVER"
        }
    })
    println(consumeOne { x -> x })

    println(consumeTwo { x, y -> x + "-" + y })

    println(consumeThree { x, y, z -> x + y + z })
    println(consumeThree { _, _, _ -> "one-two-three" })

    println(consumeTwentyTwo { _, _, _, _, _, _, _, _, _, _, _,
                               _, _, _, _, _, _, _, _, _, _, _ ->
        "one-two-...-twentythree"
    })
    println(consumeTwentyTwo { v0, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11,
                               v12, v13, v14, v15, v16, v17, v18, v19, v20, v21 ->
        v0 + v1 + v2 + v3 + v4 + v5 + v6 + v7 + v8 + v9 + v10 + v11 +
                v12 + v13 + v14 + v15 + v16 + v17 + v18 + v19 + v20 + v21
    })
}

private fun consumePrimitive(i: Int, l: (Int, Short) -> Int) = l(i, 5)

private fun testPrimitive() {
    println(consumePrimitive(1, { x, y -> x }))
    println(consumePrimitive(2, { x, y -> y.toInt() }))
    println(consumePrimitive(3, { x, y -> x + y }))
    println(consumePrimitive(4, { x, y -> x * y }))
    val l: (Int, Short) -> Int = { x, y -> x / y }
    println(l(100, 20))
}

private fun consumeUnit(i: Int, l: (Int, Short) -> Unit) = l(i, 10)

private fun testUnit() {
    println(consumeUnit(11, { x, y -> println() }))
    println(consumeUnit(12, { x, y -> println(y) }))
    println(consumeUnit(13, { x, y -> println(x) }))
    println(consumeUnit(14, { x, y -> println("$x -- $y") }))
}

