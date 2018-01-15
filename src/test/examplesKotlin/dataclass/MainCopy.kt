// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package dataclass

/**
 * This is an example of copying an instance of a data class Person using its copy method and
 * passing a value for each property.
 *
 * See https://kotlinlang.org/docs/reference/data-classes.html#copying.
 */
object MainCopy {
    @JvmStatic
    fun main(args: Array<String>) {
        testMethod()
    }

    fun testMethod() {
        val albert = Person("Albert", 28)
        val olderJonas = albert.copy("Jonas", albert.age + 10)
        println("Name: ${olderJonas.name}")
        println("Age: ${olderJonas.age}")
    }
}