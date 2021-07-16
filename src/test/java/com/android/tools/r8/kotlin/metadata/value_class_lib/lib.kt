// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata.value_class_lib

// These examples are from the kotlinlang.org website.

interface Printable {
  fun prettyPrint()
}

@JvmInline
value class Name(val s: String) : Printable {

  init {
    require(s.length > 0) { }
  }

  val length: Int
    get() = s.length

  override fun prettyPrint() {
    println("Hello, $s")
  }
}

@JvmInline
value class UInt(val x: Int)

// Represented as 'public final void compute(int x)' on the JVM
fun compute(x: Int) {
  println(x);
}

// Also represented as 'public final void compute(int x)' on the JVM thus name is mangled!
fun compute(x: UInt) {
  println(x);
}
