// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata.allow_access_modification_lib

internal class Lib internal constructor(
  internal val readOnlyPropPrimaryInternal: Int,
  internal var propPrimaryInternal : Int,
  private val readOnlyPropPrimaryPrivate: Int,
  internal var propPrimaryPrivate : Int) {

  internal val propInternal: Int = 42
  private var propPrivate: Int = 0

  private fun funPrivate() {
    println("funPrivate")
  }

  internal fun funInternal() {
    println("funInternal")
  }

  protected fun funProtected() {
    println("funProtected")
  }

  // Keep this internal to ensure we do not modify inline functions.
  internal inline fun funInline() {
    println("funInline")
  }

  internal companion object Comp {

    private fun companionPrivate() {
      println("companionPrivate")
    }

    internal fun companionInternal() {
      println("companionInternal")
    }
  }
}

private fun Lib.extensionPrivate() {
  println("extensionPrivate")
}

internal fun Lib.extensionInternal() {
  println("extensionInternal")
}

private fun staticPrivate() {
  println("staticPrivate")
}

internal fun staticInternal() {
  println("staticInternal")
}
