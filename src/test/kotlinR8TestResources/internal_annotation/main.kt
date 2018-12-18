// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package internal_annotation

fun main(args: Array<String>) {
  val i = Impl(false)
  println("$i")
  println("${i.foo()}")
}
