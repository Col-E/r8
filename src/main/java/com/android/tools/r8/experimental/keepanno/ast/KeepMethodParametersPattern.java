// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.experimental.keepanno.ast;

public abstract class KeepMethodParametersPattern {

  public static KeepMethodParametersPattern any() {
    return Any.getInstance();
  }

  public static KeepMethodParametersPattern none() {
    return None.getInstance();
  }

  private static class None extends KeepMethodParametersPattern {
    private static None INSTANCE = null;

    public static None getInstance() {
      if (INSTANCE == null) {
        INSTANCE = new None();
      }
      return INSTANCE;
    }
  }

  private static class Any extends KeepMethodParametersPattern {
    private static Any INSTANCE = null;

    public static Any getInstance() {
      if (INSTANCE == null) {
        INSTANCE = new Any();
      }
      return INSTANCE;
    }
  }
}
