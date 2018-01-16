// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package dataclass

/**
 * This is an example of copying an instance of a data class Person using its copy method and
 * relying on default values for some of its properties. Therefore the compiler will generate an
 * invoke to copy$default method which is a wrapper around copy and deals with default values.
 *
 * See https://kotlinlang.org/docs/reference/data-classes.html#copying.
 */
fun main(args: Array<String>) {
    testDataClassCopyWithDefault()
}

fun testDataClassCopyWithDefault() {
    val albert = Person("Albert", 28)
    // We don't pass a 'name', thus we copy the property value of the receiver. This will result
    // in calling the copy$default method instead of the copy method.
    val olderAlbert = albert.copy(age = albert.age + 10)
    println("Name: ${olderAlbert.name}")
    println("Age: ${olderAlbert.age}")
}