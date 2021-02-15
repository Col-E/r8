// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnostic.internal;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import java.util.function.Consumer;

public class MissingDefinitionMethodContext extends MissingDefinitionContextBase {

  private final MethodReference methodReference;

  private MissingDefinitionMethodContext(MethodReference methodReference, Origin origin) {
    super(origin);
    this.methodReference = methodReference;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public ClassReference getClassReference() {
    return methodReference.getHolderClass();
  }

  @Override
  public void getReference(
      Consumer<ClassReference> classReferenceConsumer,
      Consumer<FieldReference> fieldReferenceConsumer,
      Consumer<MethodReference> methodReferenceConsumer) {
    methodReferenceConsumer.accept(methodReference);
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
    public MissingDefinitionMethodContext build() {
      assert validate();
      return new MissingDefinitionMethodContext(methodReference, origin);
    }

    @Override
    public boolean validate() {
      super.validate();
      assert methodReference != null;
      return true;
    }
  }
}
