// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package minify_enum

enum class MinifyEnum(
    val nullableStr1: String?,
    val nullableStr2: String?,
    val number3: String
) {
  UNKNOWN(null, null, "")
}

fun main(args: Array<String>) {
  val a = MinifyEnum.UNKNOWN
}
