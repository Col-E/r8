// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

/** Pattern for matching the "extends" or "implements" clause of a class. */
public abstract class KeepExtendsPattern {

  public static KeepExtendsPattern any() {
    return Some.getAnyInstance();
  }

  public static class Builder {

    private KeepExtendsPattern pattern = KeepExtendsPattern.any();

    private Builder() {}

    public Builder classPattern(KeepQualifiedClassNamePattern pattern) {
      this.pattern = new Some(pattern);
      return this;
    }

    public KeepExtendsPattern build() {
      return pattern;
    }
  }

  private static class Some extends KeepExtendsPattern {

    private static final KeepExtendsPattern ANY_INSTANCE =
        new Some(KeepQualifiedClassNamePattern.any());

    private static KeepExtendsPattern getAnyInstance() {
      return ANY_INSTANCE;
    }

    private final KeepQualifiedClassNamePattern pattern;

    public Some(KeepQualifiedClassNamePattern pattern) {
      assert pattern != null;
      this.pattern = pattern;
    }

    @Override
    public boolean isAny() {
      return pattern.isAny();
    }

    @Override
    public KeepQualifiedClassNamePattern asClassNamePattern() {
      return pattern;
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
      Some that = (Some) o;
      return pattern.equals(that.pattern);
    }

    @Override
    public int hashCode() {
      return pattern.hashCode();
    }

    @Override
    public String toString() {
      return pattern.toString();
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  private KeepExtendsPattern() {}

  public abstract boolean isAny();

  public abstract KeepQualifiedClassNamePattern asClassNamePattern();
}
