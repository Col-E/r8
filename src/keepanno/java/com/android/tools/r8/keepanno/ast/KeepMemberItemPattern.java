// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.ast;

import java.util.Collection;
import java.util.Objects;

public class KeepMemberItemPattern extends KeepItemPattern {

  public static KeepMemberItemPattern any() {
    return builder().build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private KeepClassItemReference classReference =
        KeepClassItemPattern.any().toClassItemReference();
    private KeepMemberPattern memberPattern = KeepMemberPattern.allMembers();

    private Builder() {}

    public Builder copyFrom(KeepMemberItemPattern pattern) {
      return setClassReference(pattern.getClassReference())
          .setMemberPattern(pattern.getMemberPattern());
    }

    public Builder setClassReference(KeepClassItemReference classReference) {
      this.classReference = classReference;
      return this;
    }

    public Builder setMemberPattern(KeepMemberPattern memberPattern) {
      this.memberPattern = memberPattern;
      return this;
    }

    public KeepMemberItemPattern build() {
      return new KeepMemberItemPattern(classReference, memberPattern);
    }
  }

  private final KeepClassItemReference classReference;
  private final KeepMemberPattern memberPattern;

  private KeepMemberItemPattern(
      KeepClassItemReference classReference, KeepMemberPattern memberPattern) {
    assert classReference != null;
    assert memberPattern != null;
    this.classReference = classReference;
    this.memberPattern = memberPattern;
  }

  @Override
  public KeepMemberItemPattern asMemberItemPattern() {
    return this;
  }

  @Override
  public KeepItemReference toItemReference() {
    return KeepMemberItemReference.fromMemberItemPattern(this);
  }

  public KeepClassItemReference getClassReference() {
    return classReference;
  }

  public KeepMemberPattern getMemberPattern() {
    return memberPattern;
  }

  public Collection<KeepBindingReference> getBindingReferences() {
    return classReference.getBindingReferences();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof KeepMemberItemPattern)) {
      return false;
    }
    KeepMemberItemPattern that = (KeepMemberItemPattern) obj;
    return classReference.equals(that.classReference) && memberPattern.equals(that.memberPattern);
  }

  @Override
  public int hashCode() {
    return Objects.hash(classReference, memberPattern);
  }

  @Override
  public String toString() {
    return "KeepMemberItemPattern"
        + "{ class="
        + classReference
        + ", members="
        + memberPattern
        + '}';
  }
}
