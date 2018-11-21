// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.staticizer.trivial;

import com.android.tools.r8.NeverInline;

public class SimpleWithParams {
  static SimpleWithParams INSTANCE = new SimpleWithParams(123);

  SimpleWithParams(int i) {
  }

  @NeverInline
  String foo() {
    return bar("SimpleWithParams::foo()");
  }

  @NeverInline
  String bar(String other) {
    return "SimpleWithParams::bar(" + other + ")";
  }
}
