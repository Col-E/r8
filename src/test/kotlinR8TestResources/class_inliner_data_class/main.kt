// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package class_inliner_data_class

fun main(args: Array<String>) {
    val alpha = Alpha("", "m", "")
    alpha.right = "l"
    alpha.left = "r"
    alpha.rotate()
    println("result: ${alpha.toString()}")
}

data class Alpha(var left: String, val middle: String, var right: String) {
    fun rotate() {
        val t = left
        left = right
        right = t
    }
}
