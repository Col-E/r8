// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package dataclass

/**
 * This is an example of destructuring declaration where we retrieve some properties of the data
 * class Person (but not all) using some of its componentN functions.
 *
 * See https://kotlinlang.org/docs/reference/multi-declarations.html.
 */
object MainComponentPartial {
    @JvmStatic
    fun main(args: Array<String>) {
        testMethod()
    }

    fun testMethod() {
        // We only access to age (tied to component2 method)
        val (_, age) = Person("Albert", 28)
        println("Age: $age")
    }
}