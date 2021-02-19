// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnostic.internal;

import com.android.tools.r8.diagnostic.MissingDefinitionFieldContext;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.FieldReference;

public class MissingDefinitionFieldContextImpl extends MissingDefinitionContextBase
    implements MissingDefinitionFieldContext {

  private final FieldReference fieldReference;

  private MissingDefinitionFieldContextImpl(FieldReference fieldReference, Origin origin) {
    super(origin);
    this.fieldReference = fieldReference;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public FieldReference getFieldReference() {
    return fieldReference;
  }

  public static class Builder extends MissingDefinitionContextBase.Builder<Builder> {

    private FieldReference fieldReference;

    private Builder() {}

    public Builder setFieldContext(FieldReference fieldReference) {
      this.fieldReference = fieldReference;
      return this;
    }

    @Override
    Builder self() {
      return this;
    }

    @Override
    public MissingDefinitionFieldContextImpl build() {
      assert validate();
      return new MissingDefinitionFieldContextImpl(fieldReference, origin);
    }

    @Override
    public boolean validate() {
      super.validate();
      assert fieldReference != null;
      return true;
    }
  }
}
