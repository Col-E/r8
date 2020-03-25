// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata.companion_app

import com.android.tools.r8.kotlin.metadata.companion_lib.B

fun main() {
  B().doStuff()
  B.elt1.doStuff()
  B.elt2.doStuff()
  println(B.foo)
  println(B.bar)
  B.bar = "Hello World!";
  println(B.bar)
}
