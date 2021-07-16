// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata.value_class_app

import com.android.tools.r8.kotlin.metadata.value_class_lib.*

fun main() {
  Name("John Doe").prettyPrint()
  compute(42)
  compute(UInt(42))
}