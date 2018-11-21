// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.staticizer.trivial;

import com.android.tools.r8.NeverInline;

public class SimpleWithPhi {
  public static class Companion {
    @NeverInline
    String foo() {
      return bar("SimpleWithPhi$Companion::foo()");
    }

    @NeverInline
    String bar(String other) {
      return "SimpleWithPhi$Companion::bar(" + other + ")";
    }
  }

  static Companion INSTANCE = new Companion();

  @NeverInline
  static String foo() {
    return INSTANCE.foo();
  }

  @NeverInline
  static String bar(String other) {
    if (other.length() > 2) {
      return INSTANCE.foo();
    } else {
      return INSTANCE.bar(other);
    }
  }
}
