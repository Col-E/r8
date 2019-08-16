// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package intrinsics

fun isSupported(flag: String): Boolean {
  return flag in arrayOf("--foo", "--bar", "--baz")
}

fun containsArray() {
  println("1" in arrayOf("1", "2", "3"))
}
