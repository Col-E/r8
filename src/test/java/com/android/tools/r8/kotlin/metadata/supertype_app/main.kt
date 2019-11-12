// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata.supertype_app

import com.android.tools.r8.kotlin.metadata.supertype_lib.Impl

class ProgramClass : Impl() {
  override fun foo() {
    super.foo()
    println("Program::foo")
  }
}

fun main(args: Array<String>) {
  ProgramClass().foo()
}