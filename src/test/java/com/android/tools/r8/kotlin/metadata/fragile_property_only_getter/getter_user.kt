// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata.fragile_property_only_getter

import com.android.tools.r8.kotlin.metadata.fragile_property_lib.Person

fun main() {
  val x = Person("John Doe", 42)
  val y = Person("Hey Jude", 48)

  println(x.canDrink)
  println(x.married)
  println(y.name)
}