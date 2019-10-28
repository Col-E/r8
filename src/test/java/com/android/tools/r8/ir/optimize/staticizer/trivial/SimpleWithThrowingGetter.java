// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.staticizer.trivial;

import com.android.tools.r8.NeverInline;

public class SimpleWithThrowingGetter {
  private static SimpleWithThrowingGetter INSTANCE = new SimpleWithThrowingGetter();

  static SimpleWithThrowingGetter getInstance() {
    if (System.currentTimeMillis() < 0) {
      throw new AssertionError("This should not happen!");
    }
    return INSTANCE;
  }

  @NeverInline
  String foo() {
    return bar("Simple::foo()");
  }

  @NeverInline
  String bar(String other) {
    return "Simple::bar(" + other + ")";
  }
}
