// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import java.util.Objects;

public final class KeepFieldPattern extends KeepMemberPattern {

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private KeepFieldAccessPattern accessPattern = KeepFieldAccessPattern.any();
    private KeepFieldNamePattern namePattern = null;
    private KeepFieldTypePattern typePattern = KeepFieldTypePattern.any();

    private Builder() {}

    public Builder self() {
      return this;
    }

    public Builder setAccessPattern(KeepFieldAccessPattern accessPattern) {
      this.accessPattern = accessPattern;
      return self();
    }

    public Builder setNamePattern(KeepFieldNamePattern namePattern) {
      this.namePattern = namePattern;
      return self();
    }

    public Builder setTypePattern(KeepFieldTypePattern typePattern) {
      this.typePattern = typePattern;
      return self();
    }

    public KeepFieldPattern build() {
      if (namePattern == null) {
        throw new KeepEdgeException("Field pattern must declare a name pattern");
      }
      return new KeepFieldPattern(accessPattern, namePattern, typePattern);
    }
  }

  private final KeepFieldAccessPattern accessPattern;
  private final KeepFieldNamePattern namePattern;
  private final KeepFieldTypePattern typePattern;

  private KeepFieldPattern(
      KeepFieldAccessPattern accessPattern,
      KeepFieldNamePattern namePattern,
      KeepFieldTypePattern typePattern) {
    assert accessPattern != null;
    assert namePattern != null;
    assert typePattern != null;
    this.accessPattern = accessPattern;
    this.namePattern = namePattern;
    this.typePattern = typePattern;
  }

  @Override
  public KeepFieldPattern asField() {
    return this;
  }

  public boolean isAnyField() {
    return accessPattern.isAny() && namePattern.isAny() && typePattern.isAny();
  }

  public KeepFieldAccessPattern getAccessPattern() {
    return accessPattern;
  }

  public KeepFieldNamePattern getNamePattern() {
    return namePattern;
  }

  public KeepFieldTypePattern getTypePattern() {
    return typePattern;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    KeepFieldPattern that = (KeepFieldPattern) o;
    return accessPattern.equals(that.accessPattern)
        && namePattern.equals(that.namePattern)
        && typePattern.equals(that.typePattern);
  }

  @Override
  public int hashCode() {
    return Objects.hash(accessPattern, namePattern, typePattern);
  }

  @Override
  public String toString() {
    return "KeepFieldPattern{"
        + "access="
        + accessPattern
        + ", name="
        + namePattern
        + ", type="
        + typePattern
        + '}';
  }
}
