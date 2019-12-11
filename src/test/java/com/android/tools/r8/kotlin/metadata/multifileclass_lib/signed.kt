// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
@file:kotlin.jvm.JvmMultifileClass
@file:kotlin.jvm.JvmName("UtilKt")
package com.android.tools.r8.kotlin.metadata.multifileclass_lib

@kotlin.jvm.JvmName("joinOfInt")
public fun Sequence<Int>.join(separator: String): String {
  return fold("") { acc, i -> "$acc$separator$i" }
}

@kotlin.jvm.JvmName("commaSeparatedJoinOfInt")
public fun Sequence<Int>.join(): String {
  return join(", ")
}
