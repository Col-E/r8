// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import java.util.Objects;

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
public class KeepItemPattern {

  public static KeepItemPattern any() {
    KeepItemPattern any = builder().any().build();
    assert any.isAny();
    return any;
  }

  public static Builder builder() {
    return new Builder();
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
      return new KeepItemPattern(classNamePattern, extendsPattern, membersPattern);
    }
  }

  private final KeepQualifiedClassNamePattern qualifiedClassPattern;
  private final KeepExtendsPattern extendsPattern;
  private final KeepMembersPattern membersPattern;
  // TODO: class annotations

  private KeepItemPattern(
      KeepQualifiedClassNamePattern qualifiedClassPattern,
      KeepExtendsPattern extendsPattern,
      KeepMembersPattern membersPattern) {
    assert qualifiedClassPattern != null;
    assert extendsPattern != null;
    assert membersPattern != null;
    this.qualifiedClassPattern = qualifiedClassPattern;
    this.extendsPattern = extendsPattern;
    this.membersPattern = membersPattern;
  }

  public boolean isAny() {
    return qualifiedClassPattern.isAny() && extendsPattern.isAny() && membersPattern.isAll();
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

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    KeepItemPattern that = (KeepItemPattern) obj;
    return qualifiedClassPattern.equals(that.qualifiedClassPattern)
        && extendsPattern.equals(that.extendsPattern)
        && membersPattern.equals(that.membersPattern);
  }

  @Override
  public int hashCode() {
    return Objects.hash(qualifiedClassPattern, extendsPattern, membersPattern);
  }

  @Override
  public String toString() {
    return "KeepClassPattern{"
        + "qualifiedClassPattern="
        + qualifiedClassPattern
        + ", extendsPattern="
        + extendsPattern
        + ", membersPattern="
        + membersPattern
        + '}';
  }
}
