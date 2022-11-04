// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

/** Pattern for matching the "extends" or "implements" clause of a class. */
public abstract class KeepExtendsPattern {

  public static KeepExtendsPattern any() {
    return Any.getInstance();
  }

  public static class Builder {

    private KeepExtendsPattern pattern;

    private Builder() {}

    public Builder any() {
      pattern = Any.getInstance();
      return this;
    }

    public Builder classPattern(KeepQualifiedClassNamePattern pattern) {
      this.pattern = new Some(pattern);
      return this;
    }
  }

  private static class Any extends KeepExtendsPattern {

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

  private static class Some extends KeepExtendsPattern {

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
}
