// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package reprocess_merged_lambdas_kstyle

private var COUNT = 11

private fun next() = "${COUNT++}"

fun consumeOne(l: (x: String) -> String): String = l(next())

fun main(args: Array<String>) {
  println(consumeOne { consumeOne { consumeOne { _ -> "A" } } })
  println(consumeOne { consumeOne { consumeOne { _ -> "B" } } })
}