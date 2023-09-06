// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.optimize.defaultarguments.kt

lateinit var f: ByteArray

fun main(args: Array<String>) {
  val byteArray = ByteArray(4, { it.toByte() })
  f = byteArray
  read(byteArray, len=1)
  read(byteArray, len=2)
  read(len=3)
  read(len=4)
}

fun read(b: ByteArray = f, off: Int = 0, len: Int = b.size) {
  val start = off
  val end = off + len - 1
  for (i in start..end) {
    println(b.get(i))
  }
}