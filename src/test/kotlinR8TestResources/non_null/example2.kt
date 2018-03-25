// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package non_null

inline fun coalesce(a: String?, b: String?): String? = a ?: b
fun aOrDefault(a: String?, default: String): String =
        coalesce(a, default) ?: throw AssertionError()

fun main(args: Array<String>) {
    println(aOrDefault(null, "null"))
    println(aOrDefault("null", "non-null"))
}