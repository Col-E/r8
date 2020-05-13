// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata.anonymous_lib

class Test {

  abstract class A {
    abstract fun foo() : String;
  }

  private val internalProp = object : A() {
    override fun foo(): String {
      return "foo";
    }
  }

  val prop = internalProp
}