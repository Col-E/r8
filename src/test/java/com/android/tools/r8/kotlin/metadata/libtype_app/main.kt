// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata.libtype_app

import com.android.tools.r8.kotlin.metadata.libtype_lib_base.Sub
import com.android.tools.r8.kotlin.metadata.libtype_lib_ext.boo

fun main() {
  val s = Sub()
  s.foo()
  val b = s.boo()
  println(s == b)
}
