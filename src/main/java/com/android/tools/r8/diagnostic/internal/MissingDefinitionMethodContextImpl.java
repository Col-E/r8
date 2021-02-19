// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnostic.internal;

import com.android.tools.r8.diagnostic.MissingDefinitionMethodContext;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.MethodReference;

public class MissingDefinitionMethodContextImpl extends MissingDefinitionContextBase
    implements MissingDefinitionMethodContext {

  private final MethodReference methodReference;

  private MissingDefinitionMethodContextImpl(MethodReference methodReference, Origin origin) {
    super(origin);
    this.methodReference = methodReference;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public MethodReference getMethodReference() {
    return methodReference;
  }

  public static class Builder extends MissingDefinitionContextBase.Builder<Builder> {

    private MethodReference methodReference;

    private Builder() {}

    public Builder setMethodContext(MethodReference methodReference) {
      this.methodReference = methodReference;
      return this;
    }

    @Override
    Builder self() {
      return this;
    }

    @Override
    public MissingDefinitionMethodContextImpl build() {
      assert validate();
      return new MissingDefinitionMethodContextImpl(methodReference, origin);
    }

    @Override
    public boolean validate() {
      super.validate();
      assert methodReference != null;
      return true;
    }
  }
}
