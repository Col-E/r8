// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata.crossinline_block_lib

public inline fun foo(
  bar: String? = null,
  crossinline block: () -> String? = { null }
) {
  block()
}

public inline fun bar(
  bar1: Int = 42,
  bar2: Int = 42,
  bar3: Int = 42,
  bar4: Int = 42,
  bar5: Int = 42,
  bar6: Int = 42,
  bar7: Int = 42,
  bar8: Int = 42,
  bar9: Int = 42,
  bar10: Int = 42,
  bar11: Int = 42,
  bar12: Int = 42,
  bar13: Int = 42,
  bar14: Int = 42,
  bar15: Int = 42,
  bar16: Int = 42,
  bar17: Int = 42,
  bar18: Int = 42,
  bar19: Int = 42,
  bar20: Int = 42,
  bar21: Int = 42,
  bar22: Int = 42,
  bar23: Int = 42,
  bar24: Int = 42,
  bar25: Int = 42,
  bar26: Int = 42,
  bar27: Int = 42,
  bar28: Int = 42,
  bar29: Int = 42,
  bar30: Int = 42,
  bar31: Int = 42,
  bar32: Int = 42,
  bar33: Int = 42
) {
  println(bar1)
  println(bar2)
  println(bar3)
  println(bar4)
  println(bar5)
  println(bar6)
  println(bar7)
  println(bar8)
  println(bar9)
  println(bar10)
  println(bar11)
  println(bar12)
  println(bar13)
  println(bar14)
  println(bar15)
  println(bar16)
  println(bar17)
  println(bar18)
  println(bar19)
  println(bar20)
  println(bar21)
  println(bar22)
  println(bar23)
  println(bar24)
  println(bar25)
  println(bar26)
  println(bar27)
  println(bar28)
  println(bar29)
  println(bar30)
  println(bar31)
  println(bar32)
  println(bar33)
}