// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package intrinsics

fun foo(field: String) {
  println("field is " + field)
}

fun bar(field : String) {
  println(field)
  foo(field)
}

fun main(args : Array<String>) {
  bar((if (args.size > 0) args.get(0) else null) as String)
}