// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnostic.internal;

import com.android.tools.r8.diagnostic.DefinitionContext;
import com.android.tools.r8.diagnostic.MissingDefinitionInfo;
import com.android.tools.r8.diagnostic.MissingMethodInfo;
import com.android.tools.r8.diagnostic.internal.MissingClassInfoImpl.Builder;
import com.android.tools.r8.references.MethodReference;
import java.util.Collection;

public class MissingMethodInfoImpl extends MissingDefinitionInfoBase implements MissingMethodInfo {

  private final MethodReference methodReference;

  private MissingMethodInfoImpl(
      MethodReference methodReference, Collection<DefinitionContext> referencedFromContexts) {
    super(referencedFromContexts);
    this.methodReference = methodReference;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public MethodReference getMethodReference() {
    return methodReference;
  }

  public static class Builder extends MissingDefinitionInfoBase.Builder<Builder> {

    private MethodReference methodReference;

    private Builder() {}

    public Builder setMethod(MethodReference methodReference) {
      this.methodReference = methodReference;
      return this;
    }

    public MissingDefinitionInfo build() {
      return new MissingMethodInfoImpl(methodReference, referencedFromContextsBuilder.build());
    }

    @Override
    Builder self() {
      return this;
    }
  }
}
