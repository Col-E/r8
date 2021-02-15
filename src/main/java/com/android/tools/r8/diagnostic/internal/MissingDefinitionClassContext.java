// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnostic.internal;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import java.util.function.Consumer;

public class MissingDefinitionClassContext extends MissingDefinitionContextBase {

  private final ClassReference classReference;

  private MissingDefinitionClassContext(ClassReference classReference, Origin origin) {
    super(origin);
    this.classReference = classReference;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public ClassReference getClassReference() {
    return classReference;
  }

  @Override
  public void getReference(
      Consumer<ClassReference> classReferenceConsumer,
      Consumer<FieldReference> fieldReferenceConsumer,
      Consumer<MethodReference> methodReferenceConsumer) {
    classReferenceConsumer.accept(classReference);
  }

  public static class Builder extends MissingDefinitionContextBase.Builder<Builder> {

    private ClassReference classReference;

    private Builder() {}

    public Builder setClassContext(ClassReference classReference) {
      this.classReference = classReference;
      return this;
    }

    @Override
    Builder self() {
      return this;
    }

    @Override
    public MissingDefinitionClassContext build() {
      assert validate();
      return new MissingDefinitionClassContext(classReference, origin);
    }

    @Override
    public boolean validate() {
      super.validate();
      assert classReference != null;
      return true;
    }
  }
}
