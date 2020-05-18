// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata.inline_property_app

import com.android.tools.r8.kotlin.metadata.inline_property_lib.Lib
import com.android.tools.r8.kotlin.metadata.inline_property_lib.is7

fun main() {
  println(Lib(42).is42)
  println(Lib(42).is7)
  println(Lib(7).is42)
  println(Lib(7).is7)
}