// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.assertions.kotlintestclasses

fun main() {
  try {
    Class1().m()
  } catch (e: AssertionError) {
    println("AssertionError in Class1")
  }
  try {
    Class2().m()
  } catch (e: AssertionError) {
    println("AssertionError in Class2")
  }
  println("DONE")
}