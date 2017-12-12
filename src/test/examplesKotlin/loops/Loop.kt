// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package loops

fun loopOnArray(array: Array<Int>) {
  for (i in array) {
    println(i)
  }
}

fun main(args: Array<String>) {
  loopOnArray(arrayOf(1, 2, 3, 4, 5))
}