// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata.function_lib

interface I {
  fun doStuff()
}

open class Super : I {
  override fun doStuff() {
    println("do stuff")
  }

  fun foo() {
    println("Super::foo")
  }
}

class B : Super()

fun `fun`(b: B) {
  b.doStuff()
}
