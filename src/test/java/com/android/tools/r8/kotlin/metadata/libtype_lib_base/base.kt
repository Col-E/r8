// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata.libtype_lib_base

open class Base {
  open fun foo() {
    println("Base::foo")
  }
}

class Sub : Base() {
  override fun foo() {
    println("Sub::foo")
  }
}
