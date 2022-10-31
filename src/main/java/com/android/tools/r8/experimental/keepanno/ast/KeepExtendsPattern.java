// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.experimental.keepanno.ast;

/** Pattern for matching the "extends" or "implements" clause of a class. */
public abstract class KeepExtendsPattern {

  public static KeepExtendsPattern any() {
    return KeepExtendsAnyPattern.getInstance();
  }

  public static class Builder {

    private KeepExtendsPattern pattern;

    private Builder() {}

    public Builder any() {
      pattern = KeepExtendsAnyPattern.getInstance();
      return this;
    }

    public Builder classPattern(KeepQualifiedClassNamePattern pattern) {
      this.pattern = new KeepExtendsClassPattern(pattern);
      return this;
    }
  }

  private static class KeepExtendsAnyPattern extends KeepExtendsPattern {

    private static KeepExtendsAnyPattern INSTANCE = null;

    public static KeepExtendsAnyPattern getInstance() {
      if (INSTANCE == null) {
        INSTANCE = new KeepExtendsAnyPattern();
      }
      return INSTANCE;
    }

    @Override
    public boolean isAny() {
      return true;
    }
  }

  private static class KeepExtendsClassPattern extends KeepExtendsPattern {

    private final KeepQualifiedClassNamePattern pattern;

    public KeepExtendsClassPattern(KeepQualifiedClassNamePattern pattern) {
      this.pattern = pattern;
    }

    @Override
    public boolean isAny() {
      return pattern.isAny();
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  private KeepExtendsPattern() {}

  public abstract boolean isAny();
}
