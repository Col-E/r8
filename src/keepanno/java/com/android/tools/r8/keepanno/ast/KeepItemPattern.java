// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.ast.KeepBindings.BindingSymbol;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Predicate;

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
    return builder().any().build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isClassAndMemberPattern() {
    return kind == KeepItemKind.CLASS_AND_MEMBERS;
  }

  public boolean isClassItemPattern() {
    return kind == KeepItemKind.ONLY_CLASS;
  }

  public boolean isMemberItemPattern() {
    return kind == KeepItemKind.ONLY_MEMBERS;
  }

  public static class Builder {

    private KeepItemKind kind = null;
    private KeepClassReference classReference =
        KeepClassReference.fromClassNamePattern(KeepQualifiedClassNamePattern.any());
    private KeepExtendsPattern extendsPattern = KeepExtendsPattern.any();
    private KeepMemberPattern memberPattern = KeepMemberPattern.none();

    private Builder() {}

    public Builder copyFrom(KeepItemPattern pattern) {
      return setKind(pattern.getKind())
          .setClassReference(pattern.getClassReference())
          .setExtendsPattern(pattern.getExtendsPattern())
          .setMemberPattern(pattern.getMemberPattern());
    }

    public Builder any() {
      kind = KeepItemKind.CLASS_AND_MEMBERS;
      classReference = KeepClassReference.fromClassNamePattern(KeepQualifiedClassNamePattern.any());
      extendsPattern = KeepExtendsPattern.any();
      memberPattern = KeepMemberPattern.allMembers();
      return this;
    }

    public Builder setKind(KeepItemKind kind) {
      this.kind = kind;
      return this;
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
      if (kind == null) {
        kind = memberPattern.isNone() ? KeepItemKind.ONLY_CLASS : KeepItemKind.ONLY_MEMBERS;
      }
      if (kind == KeepItemKind.ONLY_CLASS && !memberPattern.isNone()) {
        throw new KeepEdgeException(
            "Invalid kind ONLY_CLASS for item with member pattern: " + memberPattern);
      }
      if (kind == KeepItemKind.ONLY_MEMBERS && memberPattern.isNone()) {
        throw new KeepEdgeException("Invalid kind ONLY_MEMBERS for item with no member pattern");
      }
      if (kind == KeepItemKind.CLASS_AND_MEMBERS && memberPattern.isNone()) {
        throw new KeepEdgeException(
            "Invalid kind CLASS_AND_MEMBERS for item with no member pattern");
      }
      return new KeepItemPattern(kind, classReference, extendsPattern, memberPattern);
    }
  }

  private final KeepItemKind kind;
  private final KeepClassReference classReference;
  private final KeepExtendsPattern extendsPattern;
  private final KeepMemberPattern memberPattern;
  // TODO: class annotations

  private KeepItemPattern(
      KeepItemKind kind,
      KeepClassReference classReference,
      KeepExtendsPattern extendsPattern,
      KeepMemberPattern memberPattern) {
    assert kind != null;
    assert classReference != null;
    assert extendsPattern != null;
    assert memberPattern != null;
    this.kind = kind;
    this.classReference = classReference;
    this.extendsPattern = extendsPattern;
    this.memberPattern = memberPattern;
  }

  public boolean isAny(Predicate<BindingSymbol> onReference) {
    return kind.equals(KeepItemKind.CLASS_AND_MEMBERS)
        && extendsPattern.isAny()
        && memberPattern.isAllMembers()
        && classReference.isAny(onReference);
  }

  public KeepItemKind getKind() {
    return kind;
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
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    KeepItemPattern that = (KeepItemPattern) obj;
    return kind.equals(that.kind)
        && classReference.equals(that.classReference)
        && extendsPattern.equals(that.extendsPattern)
        && memberPattern.equals(that.memberPattern);
  }

  @Override
  public int hashCode() {
    return Objects.hash(kind, classReference, extendsPattern, memberPattern);
  }

  @Override
  public String toString() {
    return "KeepClassPattern{"
        + "kind="
        + kind
        + ", classReference="
        + classReference
        + ", extendsPattern="
        + extendsPattern
        + ", memberPattern="
        + memberPattern
        + '}';
  }
}
