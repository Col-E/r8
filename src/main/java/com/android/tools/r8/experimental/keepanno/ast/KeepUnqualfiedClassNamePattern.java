// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.experimental.keepanno.ast;

public abstract class KeepUnqualfiedClassNamePattern {

  public static Builder builder() {
    return new Builder();
  }

  public static KeepUnqualfiedClassNamePattern any() {
    return KeepClassNameAnyPattern.getInstance();
  }

  public static KeepUnqualfiedClassNamePattern exact(String className) {
    return builder().exact(className).build();
  }

  public static class Builder {

    private KeepUnqualfiedClassNamePattern pattern;

    public Builder any() {
      pattern = KeepClassNameAnyPattern.getInstance();
      return this;
    }

    public Builder exact(String className) {
      pattern = new KeepClassNameExactPattern(className);
      return this;
    }

    public KeepUnqualfiedClassNamePattern build() {
      if (pattern == null) {
        throw new KeepEdgeException("Invalid class name pattern: null");
      }
      return pattern;
    }
  }

  private static class KeepClassNameAnyPattern extends KeepUnqualfiedClassNamePattern {

    private static KeepClassNameAnyPattern INSTANCE = null;

    public static KeepClassNameAnyPattern getInstance() {
      if (INSTANCE == null) {
        INSTANCE = new KeepClassNameAnyPattern();
      }
      return INSTANCE;
    }

    private KeepClassNameAnyPattern() {}

    @Override
    public boolean isAny() {
      return true;
    }

    @Override
    public boolean isExact() {
      return false;
    }

    @Override
    public boolean equals(Object obj) {
      return this == obj;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }
  }

  public static class KeepClassNameExactPattern extends KeepUnqualfiedClassNamePattern {

    private final String className;

    private KeepClassNameExactPattern(String className) {
      assert className != null;
      this.className = className;
    }

    @Override
    public boolean isAny() {
      return false;
    }

    @Override
    public boolean isExact() {
      return true;
    }

    @Override
    public KeepClassNameExactPattern asExact() {
      return this;
    }

    public String getExactNameAsString() {
      return className;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      KeepClassNameExactPattern that = (KeepClassNameExactPattern) o;
      return className.equals(that.className);
    }

    @Override
    public int hashCode() {
      return className.hashCode();
    }
  }

  public abstract boolean isAny();

  public abstract boolean isExact();

  public KeepClassNameExactPattern asExact() {
    return null;
  }
}
