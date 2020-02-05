// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata.nested_lib

class Outer {
  val prop1
    get() = 42

  fun delegateInner() = Inner().inner()

  fun delegateNested(x: Int) = Nested().nested(x)

  inner class Inner {
    fun inner(): Int {
      println("Inner::inner")
      return this@Outer.prop1
    }
  }

  class Nested {
    fun nested(x: Int): Int {
      println("Nested::nested")
      return 2 * x
    }
  }
}
