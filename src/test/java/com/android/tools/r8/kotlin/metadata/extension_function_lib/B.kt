// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata.extension_function_lib

interface I {
  fun doStuff()
}

open class Super : I {
  override fun doStuff() {
    println("do stuff")
  }
}

class B : Super()

fun B.extension() {
  doStuff()
}

fun CharSequence.csHash(): Long {
  var result = 0L
  this.forEach { result = result * 8L + it.toLong() }
  return result
}

fun LongArray.longArrayHash(): Long {
  var result = 0L
  this.forEach { result = result * 8L + it }
  return result
}

fun B.myApply(apply: B.() -> Unit) {
  apply.invoke(this)
}
