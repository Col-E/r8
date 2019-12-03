// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata.propertytype_lib

interface Itf {
  val prop1: Itf
}

open class Impl(val id: Int) : Itf {

  override val prop1: Itf
    get() = this

  override fun toString(): String {
    return "Impl::$id"
  }
}