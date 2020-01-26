// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata.sealed_lib

sealed class Expr

sealed class BinOp(val e1: Expr, val e2: Expr, val op: String) : Expr() {
  override fun toString() = "$e1 $op $e2"
}

data class Num(val num: Int) : Expr()

data class Sum(val left: Expr, val right: Expr) : BinOp(left, right, "+")

object ExprFactory {
  fun createNum(i: Int): Expr = Num(i)
  fun createSum(e1: Expr, e2: Expr): Expr = Sum(e1, e2)
}

fun Expr.eval(): Int =
  when(this) {
    is Num -> num
    is Sum -> e1.eval() + e2.eval()
    else -> throw IllegalArgumentException("Unknown Expr: $this")
  }
