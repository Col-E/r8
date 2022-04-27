// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.reflection

import kotlin.reflect.full.primaryConstructor
import kotlin.time.Duration

@JvmInline
value class Value(private val rawValue: Int)

data class Data(val value: Value)

fun main() {
  // See b/230369515 for context.
  println(Data::class.primaryConstructor?.call(Value(0))?.value)
}
