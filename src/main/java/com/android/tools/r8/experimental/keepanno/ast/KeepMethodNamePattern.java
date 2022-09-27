// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.experimental.keepanno.ast;

public class KeepMethodNamePattern {

  public static KeepMethodNamePattern any() {
    return KeepMethodNameAnyPattern.getInstance();
  }

  public static KeepMethodNamePattern initializer() {
    return new KeepMethodNameExactPattern("<init>");
  }

  public static KeepMethodNamePattern exact(String methodName) {
    return new KeepMethodNameExactPattern(methodName);
  }

  private static class KeepMethodNameAnyPattern extends KeepMethodNamePattern {
    private static KeepMethodNameAnyPattern INSTANCE = null;

    public static KeepMethodNameAnyPattern getInstance() {
      if (INSTANCE == null) {
        INSTANCE = new KeepMethodNameAnyPattern();
      }
      return INSTANCE;
    }
  }

  private static class KeepMethodNameExactPattern extends KeepMethodNamePattern {
    private final String name;

    public KeepMethodNameExactPattern(String name) {
      this.name = name;
    }
  }
}
