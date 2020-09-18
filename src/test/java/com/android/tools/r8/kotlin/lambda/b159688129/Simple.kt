// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.lambda.b159688129

fun main() {
  runSimple { println("Hello1")}
  runSimple { println("Hello2")}
  runSimple { println("Hello3")}
  runSimple { println("Hello4")}
  runSimple { println("Hello5")}
  runSimple { println("Hello6")}
}

fun runSimple(cb: () -> Unit) {
  cb()
}