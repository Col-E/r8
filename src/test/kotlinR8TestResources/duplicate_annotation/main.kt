// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package duplicate_annotation

annotation class TestAnnotation

class Foo {
  @get:TestAnnotation
  val test: Int
    @TestAnnotation
    get() = 0
}

fun main() {
  println(Foo().test)
}