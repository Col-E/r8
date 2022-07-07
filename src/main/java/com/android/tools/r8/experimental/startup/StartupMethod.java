// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;

public class StartupMethod<C, M> extends StartupItem<C, M, M> {

  public StartupMethod(int flags, M reference) {
    super(flags, reference);
  }

  public static Builder<ClassReference, MethodReference> referenceBuilder() {
    return new Builder<>();
  }

  @Override
  public boolean isStartupMethod() {
    return true;
  }

  @Override
  public StartupMethod<C, M> asStartupMethod() {
    return this;
  }

  public static class Builder<C, M> extends StartupItem.Builder<C, M, Builder<C, M>> {

    @Override
    public Builder<C, M> setClassReference(C reference) {
      throw new Unreachable();
    }

    @Override
    public StartupMethod<C, M> build() {
      return new StartupMethod<>(flags, methodReference);
    }
  }
}
