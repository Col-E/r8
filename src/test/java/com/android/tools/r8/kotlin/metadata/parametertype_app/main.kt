// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata.parametertype_app

import com.android.tools.r8.kotlin.metadata.parametertype_lib.Impl

class ProgramClass : Impl() {
  override fun bar() {
    super.bar()
    println("Program::bar")
  }
}

fun main() {
  val instance = ProgramClass()
  instance.foo(instance)
}
