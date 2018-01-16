// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package dataclass

/**
 * This is an example of destructuring declaration where we retrieve every property of the data
 * class Person using all its componentN functions.
 *
 * See https://kotlinlang.org/docs/reference/multi-declarations.html.
 */
object MainComponentOnly {
    @JvmStatic
    fun main(args: Array<String>) {
        testMethod()
    }

    fun testMethod() {
        val (name, age) = Person("Albert", 28)
        println("Name: $name")
        println("Age: $age")
    }
}