// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.experimental.keepanno.ast;

public final class KeepMethodPattern extends KeepMemberPattern {

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder extends KeepMemberPattern.Builder<Builder> {

    private KeepMethodNamePattern namePattern = null;
    private KeepMethodReturnTypePattern returnTypePattern = KeepMethodReturnTypePattern.any();
    private KeepMethodParametersPattern parametersPattern = KeepMethodParametersPattern.any();

    private Builder() {}

    @Override
    public Builder self() {
      return this;
    }

    public Builder setNamePattern(KeepMethodNamePattern namePattern) {
      this.namePattern = namePattern;
      return self();
    }

    public Builder setReturnTypeVoid() {
      returnTypePattern = KeepMethodReturnTypePattern.voidType();
      return self();
    }

    public Builder setParametersPattern(KeepMethodParametersPattern parametersPattern) {
      this.parametersPattern = parametersPattern;
      return self();
    }

    public KeepMethodPattern build() {
      if (namePattern == null) {
        throw new KeepEdgeException("Method pattern must declar a name pattern");
      }
      return new KeepMethodPattern(namePattern, returnTypePattern, parametersPattern);
    }
  }

  private final KeepMethodNamePattern namePattern;
  private final KeepMethodReturnTypePattern returnTypePattern;
  private final KeepMethodParametersPattern parametersPattern;

  private KeepMethodPattern(
      KeepMethodNamePattern namePattern,
      KeepMethodReturnTypePattern returnTypePattern,
      KeepMethodParametersPattern parametersPattern) {
    this.namePattern = namePattern;
    this.returnTypePattern = returnTypePattern;
    this.parametersPattern = parametersPattern;
  }

  public boolean isAnyMethod() {
    return false;
  }
}
