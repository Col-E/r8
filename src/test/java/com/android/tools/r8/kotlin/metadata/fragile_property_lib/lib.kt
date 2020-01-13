// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata.fragile_property_lib

class Person(var name: String) {

  val firstName: String
    get() = name.split(" ").dropLast(1).joinToString(" ")

  val familyName: String
    get() = name.split(" ").last()

  constructor(name: String, age: Int) : this(name) {
    this.age = age
  }

  var age: Int = 0

  fun aging() {
    age++
  }

  val canDrink: Boolean
    get() = age >= 21

  @set:JvmName("changeMaritalStatus")
  var married: Boolean = false
}

fun Person.changeAgeLegally(newAge: Int) {
  this.age = newAge
}
