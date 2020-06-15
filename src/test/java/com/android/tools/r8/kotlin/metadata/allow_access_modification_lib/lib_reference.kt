// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata.allow_access_modification_lib

class LibReference(
  val readOnlyPropPrimaryInternal: Int,
  var propPrimaryInternal : Int,
  val readOnlyPropPrimaryPrivate: Int,
  var propPrimaryPrivate : Int) {

  val readOnlyPropInternal: Int = 42
  var propPrivate: Int = 42

  fun funPrivate() {
    println("funPrivate")
  }

  fun funInternal() {
    println("funInternal")
  }

  fun funProtected() {
    println("funProtected")
  }

  // Keep this internal to ensure we do not modify inline functions.
  internal inline fun funInline() {
    println("funInline")
  }

  companion object Factory {

    fun companionPrivate() {
      println("companionPrivate")
    }

    fun companionInternal() {
      println("companionInternal")
    }
  }
}

fun LibReference.extensionPrivate() {
  println("extensionPrivate")
}

fun LibReference.extensionInternal() {
  println("extensionInternal")
}

fun staticPrivateReference() {
  println("staticPrivate")
}

fun staticInternalReference() {
  println("staticInternal")
}
