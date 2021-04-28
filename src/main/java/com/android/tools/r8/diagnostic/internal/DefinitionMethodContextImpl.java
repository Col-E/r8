// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnostic.internal;

import com.android.tools.r8.diagnostic.DefinitionMethodContext;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.MethodReference;

public class DefinitionMethodContextImpl extends DefinitionContextBase
    implements DefinitionMethodContext {

  private final MethodReference methodReference;

  private DefinitionMethodContextImpl(MethodReference methodReference, Origin origin) {
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

  public static class Builder extends DefinitionContextBase.Builder<Builder> {

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
    public DefinitionMethodContextImpl build() {
      assert validate();
      return new DefinitionMethodContextImpl(methodReference, origin);
    }

    @Override
    public boolean validate() {
      super.validate();
      assert methodReference != null;
      return true;
    }
  }
}
