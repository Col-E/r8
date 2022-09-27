// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.experimental.keepanno.ast;

import java.util.function.Function;
import java.util.function.Supplier;

public abstract class KeepMethodNamePattern {

  public static KeepMethodNamePattern any() {
    return KeepMethodNameAnyPattern.getInstance();
  }

  public static KeepMethodNamePattern initializer() {
    return new KeepMethodNameExactPattern("<init>");
  }

  public static KeepMethodNamePattern exact(String methodName) {
    return new KeepMethodNameExactPattern(methodName);
  }

  private KeepMethodNamePattern() {}

  public boolean isAny() {
    return match(() -> true, ignore -> false);
  }

  public boolean isExact() {
    return match(() -> false, ignore -> true);
  }
  ;

  public abstract <T> T match(Supplier<T> onAny, Function<String, T> onExact);

  private static class KeepMethodNameAnyPattern extends KeepMethodNamePattern {
    private static KeepMethodNameAnyPattern INSTANCE = null;

    public static KeepMethodNameAnyPattern getInstance() {
      if (INSTANCE == null) {
        INSTANCE = new KeepMethodNameAnyPattern();
      }
      return INSTANCE;
    }

    @Override
    public <T> T match(Supplier<T> onAny, Function<String, T> onExact) {
      return onAny.get();
    }
  }

  private static class KeepMethodNameExactPattern extends KeepMethodNamePattern {
    private final String name;

    public KeepMethodNameExactPattern(String name) {
      this.name = name;
    }

    @Override
    public <T> T match(Supplier<T> onAny, Function<String, T> onExact) {
      return onExact.apply(name);
    }
  }
}
