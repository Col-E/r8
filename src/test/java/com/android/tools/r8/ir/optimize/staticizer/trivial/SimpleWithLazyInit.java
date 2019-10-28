// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.staticizer.trivial;

import com.android.tools.r8.NeverInline;

public class SimpleWithLazyInit {
  private static SimpleWithLazyInit INSTANCE = null;

  static SimpleWithLazyInit getInstance() {
    if (INSTANCE == null) {
      INSTANCE = new SimpleWithLazyInit();
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
