// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata.default_value_app

import com.android.tools.r8.kotlin.metadata.default_value_lib.applyMap

fun main() {
  val m = mapOf("A" to "a", "B" to "b", "C" to "c")
  val s = listOf("A", "B", "C").joinToString(separator = System.lineSeparator())
  println(s.applyMap(m))
}
