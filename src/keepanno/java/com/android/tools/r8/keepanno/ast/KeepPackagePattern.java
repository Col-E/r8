// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

public abstract class KeepPackagePattern {

  public static Builder builder() {
    return new Builder();
  }

  public static KeepPackagePattern any() {
    return KeepPackageAnyPattern.getInstance();
  }

  public static KeepPackagePattern top() {
    return KeepPackageTopPattern.getInstance();
  }

  public static KeepPackagePattern exact(String fullPackage) {
    return KeepPackagePattern.builder().exact(fullPackage).build();
  }

  public static class Builder {

    private KeepPackagePattern pattern;

    public Builder any() {
      pattern = KeepPackageAnyPattern.getInstance();
      return this;
    }

    public Builder top() {
      pattern = KeepPackageTopPattern.getInstance();
      return this;
    }

    public Builder exact(String fullPackage) {
      pattern =
          fullPackage.isEmpty()
              ? KeepPackagePattern.top()
              : new KeepPackageExactPattern(fullPackage);
      return this;
    }

    public KeepPackagePattern build() {
      if (pattern == null) {
        throw new KeepEdgeException("Invalid package pattern: null");
      }
      return pattern;
    }
  }

  private static final class KeepPackageAnyPattern extends KeepPackagePattern {

    private static KeepPackageAnyPattern INSTANCE = null;

    public static KeepPackageAnyPattern getInstance() {
      if (INSTANCE == null) {
        INSTANCE = new KeepPackageAnyPattern();
      }
      return INSTANCE;
    }

    private KeepPackageAnyPattern() {}

    @Override
    public boolean isAny() {
      return true;
    }

    @Override
    public boolean isTop() {
      return false;
    }

    @Override
    public boolean isExact() {
      return false;
    }

    @Override
    public boolean equals(Object obj) {
      return obj == this;
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

  private static final class KeepPackageTopPattern extends KeepPackageExactPattern {

    private static KeepPackageTopPattern INSTANCE = null;

    public static KeepPackageTopPattern getInstance() {
      if (INSTANCE == null) {
        INSTANCE = new KeepPackageTopPattern();
      }
      return INSTANCE;
    }

    private KeepPackageTopPattern() {
      super("");
    }

    @Override
    public boolean isAny() {
      return false;
    }

    @Override
    public boolean isTop() {
      return true;
    }

    @Override
    public String toString() {
      return "";
    }
  }

  public static class KeepPackageExactPattern extends KeepPackagePattern {

    private final String fullPackage;

    private KeepPackageExactPattern(String fullPackage) {
      assert fullPackage != null;
      this.fullPackage = fullPackage;
      // TODO: Verify valid package identifiers.
    }

    @Override
    public boolean isAny() {
      return false;
    }

    @Override
    public boolean isTop() {
      return fullPackage.equals("");
    }

    @Override
    public boolean isExact() {
      return true;
    }

    @Override
    public KeepPackageExactPattern asExact() {
      return this;
    }

    public String getExactPackageAsString() {
      return fullPackage;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      KeepPackageExactPattern that = (KeepPackageExactPattern) o;
      return fullPackage.equals(that.fullPackage);
    }

    @Override
    public int hashCode() {
      return fullPackage.hashCode();
    }

    @Override
    public String toString() {
      return fullPackage;
    }
  }

  public abstract boolean isAny();

  public abstract boolean isTop();

  public abstract boolean isExact();

  public KeepPackageExactPattern asExact() {
    return null;
  }
}
