// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.kotlin

import kotlin.reflect.full.primaryConstructor

class Skynet(val specialDay: java.time.LocalDateTime)

fun main() {
  // Create Skynet reflectively and call it in a 'self-aware' fashion.
  val primaryConstructor = Skynet::class.primaryConstructor
  val skynet = primaryConstructor?.call(java.time.LocalDateTime.of(1997, 8, 29, 2, 14, 0))
  val sd = skynet?.specialDay
  println("Wuhuu, my special day is: " +
            "${sd?.year}-${sd?.monthValue}-${sd?.dayOfMonth}-${sd?.hour}-${sd?.minute}")
}
