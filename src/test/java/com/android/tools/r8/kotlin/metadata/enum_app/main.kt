// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata.enum_app

import com.android.tools.r8.kotlin.metadata.enum_lib.Direction

fun main() {
  println(Direction.UP)
  println(Direction.RIGHT)
  println(Direction.DOWN)
  println(Direction.LEFT)

  Direction::class.java.enumConstants.forEach { println(it) }
}
