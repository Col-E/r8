// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing.kotlin

enum class Color {
  RED,
  GREEN,
  BLUE
}

fun main() {
  enumValues<Color>().forEach { println(it.name) }
}