// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata.flexible_upper_bound_lib

open class A<T> {

  open fun foo(t : T) {
    println("A.foo(): " + t.toString())
  }
}

class B : A<Int>() {

  override fun foo(t : Int) {
    println("B.foo(): " + t)
  }
}

class FlexibleUpperBound<T> constructor(element: A<T>) {
  var ref = java.util.concurrent.atomic.AtomicReference(element)
}