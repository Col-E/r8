// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.sealed.kt

fun string1() : String {
  return "Hello "
}

fun string2() : String {
  return "World!"
}

fun main() {
  println(string1() + string2())
  println(string1().plus(string2()))
  println("StringConcat(" + string1() + string2() + ")")
  println(StringBuilder().append("StringBuilder[").append(string1()).append(string2()).append("]"))
  var foo = "a";
  foo = foo + "b";
  println(foo + "c")
}

fun keepForNoMemberRebinding() {
  println((if (System.currentTimeMillis() > 0) "foo" else "bar")
            + (if (System.currentTimeMillis() > 0) "baz" else "qux"))
}

fun keepForNoDoubleInlining() {
  println((if (System.currentTimeMillis() > 0) "ini" else "mini")
            + (if (System.currentTimeMillis() > 0) "miny" else "moe"))
}
