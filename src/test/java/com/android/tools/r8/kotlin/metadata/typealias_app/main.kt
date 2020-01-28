// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata.typealias_app

import com.android.tools.r8.kotlin.metadata.typealias_lib.API
import com.android.tools.r8.kotlin.metadata.typealias_lib.Impl
import com.android.tools.r8.kotlin.metadata.typealias_lib.seq

class ProgramClass : Impl() {
  override fun foo(): API {
    super.foo()
    println("Program::foo")
    return this
  }
}

fun main() {
  val instance = ProgramClass()
  val l = seq(instance)
  for (api in l) {
    println(api == api.foo())
    println(api.hey())
  }
}
