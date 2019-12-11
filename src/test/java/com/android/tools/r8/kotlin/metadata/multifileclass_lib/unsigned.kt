// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
@file:kotlin.jvm.JvmMultifileClass
@file:kotlin.jvm.JvmName("UtilKt")
package com.android.tools.r8.kotlin.metadata.multifileclass_lib

@SinceKotlin("1.3")
@ExperimentalUnsignedTypes
@kotlin.jvm.JvmName("joinOfUInt")
public fun Sequence<UInt>.join(separator: String): String {
  return fold("") { acc, i -> "$acc$separator$i" }
}

@SinceKotlin("1.3")
@ExperimentalUnsignedTypes
@kotlin.jvm.JvmName("commaSeparatedJoinOfUInt")
public fun Sequence<UInt>.join(): String {
  return join(", ")
}
