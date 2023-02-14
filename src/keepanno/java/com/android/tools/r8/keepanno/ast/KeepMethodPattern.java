// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.ast.KeepMethodNamePattern.KeepMethodNameExactPattern;
import java.util.Objects;

public final class KeepMethodPattern extends KeepMemberPattern {

  public static Builder builder() {
    return new Builder();
  }

  public static KeepMethodPattern allMethods() {
    return builder().build();
  }

  public static class Builder {

    private KeepMethodAccessPattern accessPattern = KeepMethodAccessPattern.anyMethodAccess();
    private KeepMethodNamePattern namePattern = KeepMethodNamePattern.any();
    private KeepMethodReturnTypePattern returnTypePattern = KeepMethodReturnTypePattern.any();
    private KeepMethodParametersPattern parametersPattern = KeepMethodParametersPattern.any();

    private Builder() {}

    public Builder self() {
      return this;
    }

    public Builder setAccessPattern(KeepMethodAccessPattern accessPattern) {
      this.accessPattern = accessPattern;
      return self();
    }

    public Builder setNamePattern(KeepMethodNamePattern namePattern) {
      this.namePattern = namePattern;
      return self();
    }

    public Builder setReturnTypePattern(KeepMethodReturnTypePattern returnTypePattern) {
      this.returnTypePattern = returnTypePattern;
      return self();
    }

    public Builder setReturnTypeVoid() {
      return setReturnTypePattern(KeepMethodReturnTypePattern.voidType());
    }

    public Builder setParametersPattern(KeepMethodParametersPattern parametersPattern) {
      this.parametersPattern = parametersPattern;
      return self();
    }

    public KeepMethodPattern build() {
      KeepMethodReturnTypePattern returnTypePattern = this.returnTypePattern;
      KeepMethodNameExactPattern exactName = namePattern.asExact();
      if (exactName != null
          && (exactName.getName().equals("<init>") || exactName.getName().equals("<clinit>"))) {
        if (!this.returnTypePattern.isAny() && !this.returnTypePattern.isVoid()) {
          throw new KeepEdgeException("Method constructor pattern must match 'void' type.");
        }
        returnTypePattern = KeepMethodReturnTypePattern.voidType();
      }
      return new KeepMethodPattern(
          accessPattern, namePattern, returnTypePattern, parametersPattern);
    }
  }

  private final KeepMethodAccessPattern accessPattern;
  private final KeepMethodNamePattern namePattern;
  private final KeepMethodReturnTypePattern returnTypePattern;
  private final KeepMethodParametersPattern parametersPattern;

  private KeepMethodPattern(
      KeepMethodAccessPattern accessPattern,
      KeepMethodNamePattern namePattern,
      KeepMethodReturnTypePattern returnTypePattern,
      KeepMethodParametersPattern parametersPattern) {
    assert accessPattern != null;
    assert namePattern != null;
    assert returnTypePattern != null;
    assert parametersPattern != null;
    this.accessPattern = accessPattern;
    this.namePattern = namePattern;
    this.returnTypePattern = returnTypePattern;
    this.parametersPattern = parametersPattern;
  }

  @Override
  public KeepMethodPattern asMethod() {
    return this;
  }

  public boolean isAnyMethod() {
    return accessPattern.isAny()
        && namePattern.isAny()
        && returnTypePattern.isAny()
        && parametersPattern.isAny();
  }

  @Override
  public KeepMethodAccessPattern getAccessPattern() {
    return accessPattern;
  }

  public KeepMethodNamePattern getNamePattern() {
    return namePattern;
  }

  public KeepMethodReturnTypePattern getReturnTypePattern() {
    return returnTypePattern;
  }

  public KeepMethodParametersPattern getParametersPattern() {
    return parametersPattern;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    KeepMethodPattern that = (KeepMethodPattern) o;
    return accessPattern.equals(that.accessPattern)
        && namePattern.equals(that.namePattern)
        && returnTypePattern.equals(that.returnTypePattern)
        && parametersPattern.equals(that.parametersPattern);
  }

  @Override
  public int hashCode() {
    return Objects.hash(accessPattern, namePattern, returnTypePattern, parametersPattern);
  }

  @Override
  public String toString() {
    return "KeepMethodPattern{"
        + "access="
        + accessPattern
        + ", name="
        + namePattern
        + ", returnType="
        + returnTypePattern
        + ", parameters="
        + parametersPattern
        + '}';
  }
}
