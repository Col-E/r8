// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.ast;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

public class KeepClassItemPattern extends KeepItemPattern {

  public static KeepClassItemPattern any() {
    return builder().build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private KeepQualifiedClassNamePattern classNamePattern = KeepQualifiedClassNamePattern.any();
    private KeepInstanceOfPattern extendsPattern = KeepInstanceOfPattern.any();

    private Builder() {}

    public Builder copyFrom(com.android.tools.r8.keepanno.ast.KeepClassItemPattern pattern) {
      return setClassNamePattern(pattern.getClassNamePattern())
          .setInstanceOfPattern(pattern.getExtendsPattern());
    }

    public Builder setClassNamePattern(KeepQualifiedClassNamePattern classNamePattern) {
      this.classNamePattern = classNamePattern;
      return this;
    }

    public Builder setInstanceOfPattern(KeepInstanceOfPattern extendsPattern) {
      this.extendsPattern = extendsPattern;
      return this;
    }

    public com.android.tools.r8.keepanno.ast.KeepClassItemPattern build() {
      return new com.android.tools.r8.keepanno.ast.KeepClassItemPattern(
          classNamePattern, extendsPattern);
    }
  }

  private final KeepQualifiedClassNamePattern classNamePattern;
  private final KeepInstanceOfPattern extendsPattern;

  public KeepClassItemPattern(
      KeepQualifiedClassNamePattern classNamePattern, KeepInstanceOfPattern extendsPattern) {
    assert classNamePattern != null;
    assert extendsPattern != null;
    this.classNamePattern = classNamePattern;
    this.extendsPattern = extendsPattern;
  }

  @Override
  public KeepClassItemPattern asClassItemPattern() {
    return this;
  }

  @Override
  public KeepItemReference toItemReference() {
    return toClassItemReference();
  }

  public final KeepClassItemReference toClassItemReference() {
    return KeepClassItemReference.fromClassItemPattern(this);
  }

  @Override
  public Collection<KeepBindingReference> getBindingReferences() {
    return Collections.emptyList();
  }

  public KeepQualifiedClassNamePattern getClassNamePattern() {
    return classNamePattern;
  }

  public KeepInstanceOfPattern getExtendsPattern() {
    return extendsPattern;
  }

  public boolean isAny() {
    return classNamePattern.isAny() && extendsPattern.isAny();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof KeepClassItemPattern)) {
      return false;
    }
    KeepClassItemPattern that = (KeepClassItemPattern) obj;
    return classNamePattern.equals(that.classNamePattern)
        && extendsPattern.equals(that.extendsPattern);
  }

  @Override
  public int hashCode() {
    return Objects.hash(classNamePattern, extendsPattern);
  }

  @Override
  public String toString() {
    return "KeepClassItemPattern"
        + "{ class="
        + classNamePattern
        + ", extends="
        + extendsPattern
        + '}';
  }
}
