// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata.vararg_lib

class SomeClass {
  fun foo(x : String) {
    println("SomeClass::$x")
  }
}

fun bar(vararg strs: String, f: (SomeClass, String) -> Unit) {
  if (strs.isNotEmpty() && strs.any { it.startsWith("R8") }) {
    val instance = SomeClass()
    strs
      .filter { it.startsWith("R8") }
      .map { f.invoke(instance, it) }
  }
}
