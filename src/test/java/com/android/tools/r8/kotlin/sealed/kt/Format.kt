// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.sealed.kt

public sealed class Format(val name: String) {
    object Zip : Format("ZIP")
    object Directory : Format("DIRECTORY")
}

fun main() {
  val value = when ("ZIP") {
      Format.Zip.name -> Format.Zip
      Format.Directory.name -> Format.Directory
      else -> throw IllegalArgumentException(
          "Valid formats: ${Format.Zip.name} or ${Format.Directory.name}.")
  }
  println(value.name)
}