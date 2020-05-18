// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata.flexible_upper_bound_app

import com.android.tools.r8.kotlin.metadata.flexible_upper_bound_lib.B
import com.android.tools.r8.kotlin.metadata.flexible_upper_bound_lib.FlexibleUpperBound

fun main() {
  val flexible = FlexibleUpperBound(B())
  flexible.ref.get().foo(42)
  flexible.ref.set(null)
}