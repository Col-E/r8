// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.experimental.keepanno.ast;

public class KeepMethodReturnTypePattern {

  public static KeepMethodReturnTypePattern any() {
    return Any.getInstance();
  }

  public static KeepMethodReturnTypePattern voidType() {
    return VoidType.getInstance();
  }

  private static class VoidType extends KeepMethodReturnTypePattern {
    private static VoidType INSTANCE = null;

    public static VoidType getInstance() {
      if (INSTANCE == null) {
        INSTANCE = new VoidType();
      }
      return INSTANCE;
    }
  }

  private static class Any extends KeepMethodReturnTypePattern {
    private static Any INSTANCE = null;

    public static Any getInstance() {
      if (INSTANCE == null) {
        INSTANCE = new Any();
      }
      return INSTANCE;
    }
  }
}
