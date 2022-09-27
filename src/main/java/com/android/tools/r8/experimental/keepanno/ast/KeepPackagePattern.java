// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.experimental.keepanno.ast;

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
  }

  private static final class KeepPackageTopPattern extends KeepPackagePattern {

    private static KeepPackageTopPattern INSTANCE = null;

    public static KeepPackageTopPattern getInstance() {
      if (INSTANCE == null) {
        INSTANCE = new KeepPackageTopPattern();
      }
      return INSTANCE;
    }

    private KeepPackageTopPattern() {}

    @Override
    public boolean isAny() {
      return false;
    }
  }

  private static final class KeepPackageExactPattern extends KeepPackagePattern {

    private final String fullPackage;

    private KeepPackageExactPattern(String fullPackage) {
      this.fullPackage = fullPackage;
      // TODO: Verify valid package identifiers.
    }

    @Override
    public boolean isAny() {
      return false;
    }
  }

  public abstract boolean isAny();
}
