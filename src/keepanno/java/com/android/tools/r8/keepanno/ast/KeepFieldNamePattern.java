// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

public abstract class KeepFieldNamePattern {

  public static KeepFieldNamePattern any() {
    return Any.getInstance();
  }

  public static KeepFieldNamePattern exact(String methodName) {
    return new KeepFieldNameExactPattern(methodName);
  }

  private KeepFieldNamePattern() {}

  public boolean isAny() {
    return false;
  }

  public final boolean isExact() {
    return asExact() != null;
  }

  public KeepFieldNameExactPattern asExact() {
    return null;
  }

  private static class Any extends KeepFieldNamePattern {
    private static final Any INSTANCE = new Any();

    public static Any getInstance() {
      return INSTANCE;
    }

    @Override
    public boolean isAny() {
      return true;
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

  public static class KeepFieldNameExactPattern extends KeepFieldNamePattern {
    private final String name;

    public KeepFieldNameExactPattern(String name) {
      assert name != null;
      this.name = name;
    }

    @Override
    public KeepFieldNameExactPattern asExact() {
      return this;
    }

    public String getName() {
      return name;
    }

    @Override
    @SuppressWarnings("EqualsGetClass")
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      KeepFieldNameExactPattern that = (KeepFieldNameExactPattern) o;
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
