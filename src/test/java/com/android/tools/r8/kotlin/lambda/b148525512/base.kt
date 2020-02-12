// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.lambda.b148525512

fun printInt(l: () -> Int ) = println(l())

open class Base(@JvmField var x: Int, @JvmField var y: Int)

fun main(args: Array<String>) {
  val base = Base(args.size + 1, args.size + 2)
  printInt { base.x }
  printInt { base.y }
  if (FeatureAPI.hasFeature()) {
    FeatureAPI.feature(args.size + 3)
  }
}