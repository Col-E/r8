// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package intrinsics

import java.lang.reflect.InvocationTargetException

fun main(args: Array<String>) {
    testParameterNullCheck()
}

fun expectsNonNullParameters(a: String, b: String): String = a + b

fun testParameterNullCheck() {
    println("> ${expectsNonNullParameters("pre", "post")} <")

    val intrinsics = Class.forName("intrinsics.IntrinsicsKt")
    val method = intrinsics.getMethod(
            "expectsNonNullParameters", String::class.java, String::class.java)

    println("> ${method.invoke(null, "pre", "post")} <")

    try {
        println("> ${method.invoke(null, "pre", null)} <")
    } catch (e: InvocationTargetException) {
        println("> exception: ${e.targetException::javaClass} <")
        return
    }
    throw AssertionError()
}

