// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

/** Pattern for matching the instance-of properties of a class. */
public abstract class KeepInstanceOfPattern {

  public static KeepInstanceOfPattern any() {
    return Some.getAnyInstance();
  }

  public static class Builder {

    private KeepQualifiedClassNamePattern namePattern = KeepQualifiedClassNamePattern.any();
    private boolean isInclusive = true;

    private Builder() {}

    public Builder classPattern(KeepQualifiedClassNamePattern namePattern) {
      this.namePattern = namePattern;
      return this;
    }

    public Builder setInclusive(boolean isInclusive) {
      this.isInclusive = isInclusive;
      return this;
    }

    public KeepInstanceOfPattern build() {
      if (namePattern.isAny()) {
        if (!isInclusive) {
          throw new KeepEdgeException(
              "Invalid instance-of pattern matching any class exclusive. "
                  + "This pattern matches nothing.");
        }
        return any();
      }
      return new Some(namePattern, isInclusive);
    }
  }

  private static class Some extends KeepInstanceOfPattern {

    private static final KeepInstanceOfPattern ANY_INSTANCE =
        new Some(KeepQualifiedClassNamePattern.any(), true);

    private static KeepInstanceOfPattern getAnyInstance() {
      return ANY_INSTANCE;
    }

    private final KeepQualifiedClassNamePattern namePattern;
    private final boolean isInclusive;

    public Some(KeepQualifiedClassNamePattern namePattern, boolean isInclusive) {
      assert namePattern != null;
      this.namePattern = namePattern;
      this.isInclusive = isInclusive;
    }

    @Override
    public boolean isAny() {
      return namePattern.isAny();
    }

    @Override
    public boolean isInclusive() {
      return isInclusive;
    }

    @Override
    public KeepQualifiedClassNamePattern getClassNamePattern() {
      return namePattern;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Some)) {
        return false;
      }
      Some that = (Some) o;
      return namePattern.equals(that.namePattern);
    }

    @Override
    public int hashCode() {
      return namePattern.hashCode();
    }

    @Override
    public String toString() {
      String nameString = namePattern.toString();
      return isInclusive ? nameString : ("excl(" + nameString + ")");
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  private KeepInstanceOfPattern() {}

  public abstract boolean isAny();

  public abstract KeepQualifiedClassNamePattern getClassNamePattern();

  public abstract boolean isInclusive();

  public final boolean isExclusive() {
    return !isInclusive();
  }
}
