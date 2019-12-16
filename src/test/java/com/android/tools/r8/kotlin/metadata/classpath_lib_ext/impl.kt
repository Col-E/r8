// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata.classpath_lib_ext

import com.android.tools.r8.kotlin.metadata.classpath_lib_base.Itf

open class Impl : Itf {
  override fun foo() {
    println("Impl::foo")
  }
}

class Extra : Impl()

fun Extra.fooExt() {
  foo()
}
