// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b191296688

class B {
  fun doIt() {
    A().doIt(this::proceed)
  }

  private fun proceed() {
    println("hep")
  }
}

fun main() = B().doIt()