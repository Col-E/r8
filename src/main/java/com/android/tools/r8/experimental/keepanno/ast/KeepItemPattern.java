// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.experimental.keepanno.ast;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A pattern for matching items in the program.
 *
 * <p>An item pattern can be any item, or it can describe a family of classes or a family of members
 * on a classes.
 *
 * <p>A pattern cannot describe both a class *and* a member of a class. Either it is a pattern on
 * classes or it is a pattern on members. The distinction is defined by having a "none" member
 * pattern.
 */
public abstract class KeepItemPattern {

  public static Builder builder() {
    return new Builder();
  }

  public static KeepItemPattern any() {
    return KeepItemAnyPattern.getInstance();
  }

  public static class Builder {

    private KeepQualifiedClassNamePattern classNamePattern;
    private KeepExtendsPattern extendsPattern = KeepExtendsPattern.any();
    private KeepMembersPattern membersPattern = KeepMembersPattern.none();

    private Builder() {}

    public Builder any() {
      classNamePattern = KeepQualifiedClassNamePattern.any();
      extendsPattern = KeepExtendsPattern.any();
      membersPattern = KeepMembersPattern.all();
      return this;
    }

    public Builder setClassPattern(KeepQualifiedClassNamePattern qualifiedClassNamePattern) {
      this.classNamePattern = qualifiedClassNamePattern;
      return this;
    }

    public Builder setExtendsPattern(KeepExtendsPattern extendsPattern) {
      this.extendsPattern = extendsPattern;
      return this;
    }

    public Builder setMembersPattern(KeepMembersPattern membersPattern) {
      this.membersPattern = membersPattern;
      return this;
    }

    public KeepItemPattern build() {
      if (classNamePattern == null) {
        throw new KeepEdgeException("Class pattern must define a class name pattern.");
      }
      if (classNamePattern.isAny() && extendsPattern.isAny() && membersPattern.isAll()) {
        return KeepItemPattern.any();
      }
      return new KeepClassPattern(classNamePattern, extendsPattern, membersPattern);
    }
  }

  private static class KeepItemAnyPattern extends KeepItemPattern {

    private static KeepItemAnyPattern INSTANCE = null;

    public static KeepItemAnyPattern getInstance() {
      if (INSTANCE == null) {
        INSTANCE = new KeepItemAnyPattern();
      }
      return INSTANCE;
    }

    @Override
    public boolean isAny() {
      return true;
    }

    @Override
    public <T> T match(Supplier<T> onAny, Function<KeepClassPattern, T> onItem) {
      return onAny.get();
    }
  }

  public static class KeepClassPattern extends KeepItemPattern {

    private final KeepQualifiedClassNamePattern qualifiedClassPattern;
    private final KeepExtendsPattern extendsPattern;
    private final KeepMembersPattern membersPattern;
    // TODO: class annotations

    private KeepClassPattern(
        KeepQualifiedClassNamePattern qualifiedClassPattern,
        KeepExtendsPattern extendsPattern,
        KeepMembersPattern membersPattern) {
      this.qualifiedClassPattern = qualifiedClassPattern;
      this.extendsPattern = extendsPattern;
      this.membersPattern = membersPattern;
    }

    @Override
    public boolean isAny() {
      return qualifiedClassPattern.isAny() && extendsPattern.isAny() && membersPattern.isAll();
    }

    @Override
    public <T> T match(Supplier<T> onAny, Function<KeepClassPattern, T> onItem) {
      if (isAny()) {
        return onAny.get();
      } else {
        return onItem.apply(this);
      }
    }

    public KeepQualifiedClassNamePattern getClassNamePattern() {
      return qualifiedClassPattern;
    }

    public KeepExtendsPattern getExtendsPattern() {
      return extendsPattern;
    }

    public KeepMembersPattern getMembersPattern() {
      return membersPattern;
    }
  }

  public abstract boolean isAny();

  public abstract <T> T match(Supplier<T> onAny, Function<KeepClassPattern, T> onItem);
}
