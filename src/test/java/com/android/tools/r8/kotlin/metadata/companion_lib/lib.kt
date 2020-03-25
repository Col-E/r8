// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata.companion_lib

interface I {
  fun doStuff()
}

open class Super : I {
  override fun doStuff() {
    println("do stuff")
  }
}

class B : Super() {
  override fun doStuff() {
    println(foo)
  }

  companion object {
    val elt1: Super = B()
    @JvmField
    val elt2: Super = B()
    val foo: String
      get() = "B.Companion::foo"
    var bar : String = "B.Companion::bar"
      get() = field
      set(value) {
        field = value
      }
  }
}
