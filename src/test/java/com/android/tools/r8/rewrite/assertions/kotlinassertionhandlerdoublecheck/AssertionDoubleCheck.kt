// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.assertions.kotlinassertionhandlerdoublecheck

fun doubleCheckAssertionsEnabled() {
  // AssertionsMock.ENABLED will be rewritten to kotlin._Assertions.ENABLED to
  // test
  //   * two checks on kotlin._Assertions.ENABLED and
  //   * check on both kotlin._Assertions.ENABLED and $assertionsDisabled
  // before entering the assertion code.
  //
  // This is testing code like this found in kotlin-stdlib:
  //
  //   if (_Assertions.ENABLED)
  //     assert(...) { ... }
  //   }
  //
  // E.g. in kotlin/io/files/FileTreeWalk.kt.
  if (AssertionsMock.ENABLED) {
    assert(false) { "doubleCheckAssertion" }
  }
}

fun main() {
  doubleCheckAssertionsEnabled();
}