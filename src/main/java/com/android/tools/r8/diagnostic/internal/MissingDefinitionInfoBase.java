// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnostic.internal;

import com.android.tools.r8.diagnostic.DefinitionContext;
import com.android.tools.r8.diagnostic.MissingDefinitionInfo;
import com.google.common.collect.ImmutableList;
import java.util.Collection;

public abstract class MissingDefinitionInfoBase implements MissingDefinitionInfo {

  final Collection<DefinitionContext> referencedFromContexts;

  MissingDefinitionInfoBase(Collection<DefinitionContext> referencedFromContexts) {
    this.referencedFromContexts = referencedFromContexts;
  }

  @Override
  public String getDiagnosticMessage() {
    StringBuilder builder = new StringBuilder();
    MissingDefinitionInfoUtils.writeDiagnosticMessage(builder, this);
    return builder.toString();
  }

  @Override
  public final Collection<DefinitionContext> getReferencedFromContexts() {
    return referencedFromContexts;
  }

  public abstract static class Builder {

    final ImmutableList.Builder<DefinitionContext> referencedFromContextsBuilder =
        ImmutableList.builder();

    Builder() {}

    public Builder addReferencedFromContext(DefinitionContext missingDefinitionContext) {
      referencedFromContextsBuilder.add(missingDefinitionContext);
      return this;
    }
  }
}
