// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata.jvmstatic_app

import com.android.tools.r8.kotlin.metadata.jvmstatic_lib.InterfaceWithCompanion
import com.android.tools.r8.kotlin.metadata.jvmstatic_lib.Lib

fun main() {
  InterfaceWithCompanion.greet("Hello")
  Lib.staticFun { true }
  Lib.staticProp = "Foo"
  println(Lib.staticProp)
}