// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata.multifileclass_app

import com.android.tools.r8.kotlin.metadata.multifileclass_lib.join

fun signed() {
  val s = sequenceOf(1, 2, 3)
  println(s.join())
}

@OptIn(ExperimentalUnsignedTypes::class)
fun unsigned() {
  val s = sequenceOf(1u, 2u, 3u)
  println(s.join())
}

fun main() {
  signed()
  unsigned()
}
