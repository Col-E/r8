// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnostic.internal;

import com.android.tools.r8.diagnostic.DefinitionContext;
import com.android.tools.r8.diagnostic.MissingDefinitionInfo;
import com.android.tools.r8.diagnostic.MissingFieldInfo;
import com.android.tools.r8.diagnostic.internal.MissingClassInfoImpl.Builder;
import com.android.tools.r8.references.FieldReference;
import java.util.Collection;

public class MissingFieldInfoImpl extends MissingDefinitionInfoBase implements MissingFieldInfo {

  private final FieldReference fieldReference;

  private MissingFieldInfoImpl(
      FieldReference fieldReference, Collection<DefinitionContext> referencedFromContexts) {
    super(referencedFromContexts);
    this.fieldReference = fieldReference;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public FieldReference getFieldReference() {
    return fieldReference;
  }

  public static class Builder extends MissingDefinitionInfoBase.Builder<Builder> {

    private FieldReference fieldReference;

    private Builder() {}

    public Builder setField(FieldReference fieldReference) {
      this.fieldReference = fieldReference;
      return this;
    }

    public MissingDefinitionInfo build() {
      return new MissingFieldInfoImpl(fieldReference, referencedFromContextsBuilder.build());
    }

    @Override
    Builder self() {
      return this;
    }
  }
}
