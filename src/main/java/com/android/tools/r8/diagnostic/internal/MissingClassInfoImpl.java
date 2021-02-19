// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnostic.internal;

import com.android.tools.r8.diagnostic.MissingClassInfo;
import com.android.tools.r8.diagnostic.MissingDefinitionContext;
import com.android.tools.r8.diagnostic.MissingDefinitionInfo;
import com.android.tools.r8.references.ClassReference;
import java.util.Collection;

public class MissingClassInfoImpl extends MissingDefinitionInfoBase implements MissingClassInfo {

  private final ClassReference classReference;

  private MissingClassInfoImpl(
      ClassReference classReference, Collection<MissingDefinitionContext> referencedFromContexts) {
    super(referencedFromContexts);
    this.classReference = classReference;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public ClassReference getClassReference() {
    return classReference;
  }

  public static class Builder extends MissingDefinitionInfoBase.Builder {

    private ClassReference classReference;

    private Builder() {}

    public Builder setClass(ClassReference classReference) {
      this.classReference = classReference;
      return this;
    }

    public MissingDefinitionInfo build() {
      return new MissingClassInfoImpl(classReference, referencedFromContextsBuilder.build());
    }
  }
}
