// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata.sealed_app

import com.android.tools.r8.kotlin.metadata.sealed_lib.Expr
import com.android.tools.r8.kotlin.metadata.sealed_lib.eval

// A sealed class can only be extended within that file.
class MyExpr : Expr()

fun main() {
  println(MyExpr().eval())
}
