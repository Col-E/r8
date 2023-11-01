// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.ast.KeepBindings.BindingSymbol;
import java.util.Collection;
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

  public static KeepItemPattern anyClass() {
    return builder().setMemberPattern(KeepMemberPattern.none()).build();
  }

  public static KeepItemPattern anyMember() {
    return builder().setMemberPattern(KeepMemberPattern.allMembers()).build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isClassItemPattern() {
    return memberPattern.isNone();
  }

  public boolean isMemberItemPattern() {
    return !isClassItemPattern();
  }

  public static class Builder {

    private KeepClassReference classReference =
        KeepClassReference.fromClassNamePattern(KeepQualifiedClassNamePattern.any());
    private KeepExtendsPattern extendsPattern = KeepExtendsPattern.any();
    private KeepMemberPattern memberPattern = KeepMemberPattern.none();

    private Builder() {}

    public Builder copyFrom(KeepItemPattern pattern) {
      return setClassReference(pattern.getClassReference())
          .setExtendsPattern(pattern.getExtendsPattern())
          .setMemberPattern(pattern.getMemberPattern());
    }

    public Builder setClassReference(KeepClassReference classReference) {
      this.classReference = classReference;
      return this;
    }

    public Builder setClassPattern(KeepQualifiedClassNamePattern qualifiedClassNamePattern) {
      return setClassReference(KeepClassReference.fromClassNamePattern(qualifiedClassNamePattern));
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
      return new KeepItemPattern(classReference, extendsPattern, memberPattern);
    }
  }

  private final KeepClassReference classReference;
  private final KeepExtendsPattern extendsPattern;
  private final KeepMemberPattern memberPattern;
  // TODO: class annotations

  private KeepItemPattern(
      KeepClassReference classReference,
      KeepExtendsPattern extendsPattern,
      KeepMemberPattern memberPattern) {
    assert classReference != null;
    assert extendsPattern != null;
    assert memberPattern != null;
    this.classReference = classReference;
    this.extendsPattern = extendsPattern;
    this.memberPattern = memberPattern;
  }

  public KeepClassReference getClassReference() {
    return classReference;
  }

  public KeepExtendsPattern getExtendsPattern() {
    return extendsPattern;
  }

  public KeepMemberPattern getMemberPattern() {
    return memberPattern;
  }

  public Collection<BindingSymbol> getBindingReferences() {
    return classReference.getBindingReferences();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof KeepItemPattern)) {
      return false;
    }
    KeepItemPattern that = (KeepItemPattern) obj;
    return classReference.equals(that.classReference)
        && extendsPattern.equals(that.extendsPattern)
        && memberPattern.equals(that.memberPattern);
  }

  @Override
  public int hashCode() {
    return Objects.hash(classReference, extendsPattern, memberPattern);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    if (isClassItemPattern()) {
      builder.append("KeepClassPattern");
    } else {
      assert isMemberItemPattern();
      builder.append("KeepMemberPattern");
    }
    builder.append("{ class=").append(classReference);
    if (!extendsPattern.isAny()) {
      builder.append(", extends=").append(extendsPattern);
    }
    if (!memberPattern.isNone()) {
      builder.append(", members=").append(memberPattern);
    }
    return builder.append('}').toString();
  }
}
