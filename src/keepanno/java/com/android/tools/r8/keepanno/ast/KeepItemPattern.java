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
    private KeepMemberPattern memberPattern = KeepMemberPattern.none();

    private Builder() {}

    public Builder any() {
      classNamePattern = KeepQualifiedClassNamePattern.any();
      extendsPattern = KeepExtendsPattern.any();
      memberPattern = KeepMemberPattern.all();
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

    public Builder setMemberPattern(KeepMemberPattern memberPattern) {
      this.memberPattern = memberPattern;
      return this;
    }

    public KeepItemPattern build() {
      if (classNamePattern == null) {
        throw new KeepEdgeException("Class pattern must define a class name pattern.");
      }
      return new KeepItemPattern(classNamePattern, extendsPattern, memberPattern);
    }
  }

  private final KeepQualifiedClassNamePattern qualifiedClassPattern;
  private final KeepExtendsPattern extendsPattern;
  private final KeepMemberPattern memberPattern;
  // TODO: class annotations

  private KeepItemPattern(
      KeepQualifiedClassNamePattern qualifiedClassPattern,
      KeepExtendsPattern extendsPattern,
      KeepMemberPattern memberPattern) {
    assert qualifiedClassPattern != null;
    assert extendsPattern != null;
    assert memberPattern != null;
    this.qualifiedClassPattern = qualifiedClassPattern;
    this.extendsPattern = extendsPattern;
    this.memberPattern = memberPattern;
  }

  public boolean isAny() {
    return qualifiedClassPattern.isAny() && extendsPattern.isAny() && memberPattern.isAll();
  }

  public KeepQualifiedClassNamePattern getClassNamePattern() {
    return qualifiedClassPattern;
  }

  public KeepExtendsPattern getExtendsPattern() {
    return extendsPattern;
  }

  public KeepMemberPattern getMemberPattern() {
    return memberPattern;
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
        && memberPattern.equals(that.memberPattern);
  }

  @Override
  public int hashCode() {
    return Objects.hash(qualifiedClassPattern, extendsPattern, memberPattern);
  }

  @Override
  public String toString() {
    return "KeepClassPattern{"
        + "qualifiedClassPattern="
        + qualifiedClassPattern
        + ", extendsPattern="
        + extendsPattern
        + ", memberPattern="
        + memberPattern
        + '}';
  }
}
