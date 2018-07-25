// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.staticizer.trivial;

public class SimpleWithGetter {
  private static SimpleWithGetter INSTANCE = new SimpleWithGetter();

  static SimpleWithGetter getInstance() {
    return INSTANCE;
  }

  String foo() {
    synchronized ("") {
      return bar("Simple::foo()");
    }
  }

  String bar(String other) {
    synchronized ("") {
      return "Simple::bar(" + other + ")";
    }
  }
}
