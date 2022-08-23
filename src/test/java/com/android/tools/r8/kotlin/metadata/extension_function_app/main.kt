// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata.extension_function_app

import com.android.tools.r8.kotlin.metadata.extension_function_lib.B
import com.android.tools.r8.kotlin.metadata.extension_function_lib.extension
import com.android.tools.r8.kotlin.metadata.extension_function_lib.csHash
import com.android.tools.r8.kotlin.metadata.extension_function_lib.longArrayHash
import com.android.tools.r8.kotlin.metadata.extension_function_lib.myApply

fun main() {
  B().doStuff()
  B().extension()

  ("R8").csHash()
  longArrayOf(42L).longArrayHash()
  B().myApply { this.doStuff() }
}
