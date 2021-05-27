// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata.nested_reflect

import kotlin.reflect.full.primaryConstructor

class Outer {
  data class Nested(val data: Int)
  inner class Inner(val data: Int)
}

fun main() {
  val nestedPrimaryCtr = Outer.Nested::class.primaryConstructor
  println(nestedPrimaryCtr?.toString() ?: "Cannot find primary constructor")
  val innerPrimaryCtr = Outer.Inner::class.primaryConstructor
  println(innerPrimaryCtr?.toString() ?: "Cannot find primary constructor")
}
