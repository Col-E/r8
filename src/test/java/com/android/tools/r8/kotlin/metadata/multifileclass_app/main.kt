// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata.multifileclass_app

import com.android.tools.r8.kotlin.metadata.multifileclass_lib.join

fun main() {
  val s = sequenceOf(1, 2, 3)
  println(s.join())
}
