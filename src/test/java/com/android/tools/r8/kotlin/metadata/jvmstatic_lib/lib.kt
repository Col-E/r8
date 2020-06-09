// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata.jvmstatic_lib

interface InterfaceWithCompanion {
  companion object {
    @JvmStatic fun greet(username: String) {
      println("Hello, $username")
    }
  }
}

object Lib {

  @JvmStatic
  fun staticFun(func : () -> Boolean) {
    println("Calling func...")
    func()
  }

  @JvmStatic
  var staticProp : String = ""
}