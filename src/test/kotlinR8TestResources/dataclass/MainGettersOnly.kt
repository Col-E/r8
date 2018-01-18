// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package dataclass

/**
 * This is an example of accessing properties of a data class instance by only using its
 * synthesized getter methods. Therefore, the other synthesized methods (like componentN or copy)
 * can be removed after shrinking.
 *
 * See https://kotlinlang.org/docs/reference/properties.html.
 */
fun main(args: Array<String>) {
    testDataClassGetters();
}

fun testDataClassGetters() {
    val person = Person("Albert", 28)
    println("Name: ${person.name}")
    println("Age: ${person.age}")
}