// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

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

    @Override
    public boolean equals(Object obj) {
      return this == obj;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }

    @Override
    public String toString() {
      return "*";
    }
  }

  private static class KeepMethodNameExactPattern extends KeepMethodNamePattern {
    private final String name;

    public KeepMethodNameExactPattern(String name) {
      assert name != null;
      this.name = name;
    }

    @Override
    public <T> T match(Supplier<T> onAny, Function<String, T> onExact) {
      return onExact.apply(name);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      KeepMethodNameExactPattern that = (KeepMethodNameExactPattern) o;
      return name.equals(that.name);
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }

    @Override
    public String toString() {
      return name;
    }
  }
}
