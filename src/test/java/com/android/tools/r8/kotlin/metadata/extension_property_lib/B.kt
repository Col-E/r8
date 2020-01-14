// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata.extension_property_lib

interface I {
  fun doStuff()
}

open class Super : I {
  override fun doStuff() {
    println("do stuff")
  }
}

class B : Super()

val B.asI : I
  get() = this
