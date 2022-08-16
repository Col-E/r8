// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import java.util.function.Consumer;
import java.util.function.Function;

// TODO(b/238173796): When updating the compiler to have support for taking a list of startup
//  methods, this class may likely be removed along with the StartupItem class, so that only
//  StartupMethod remains.
public class StartupClass<C, M> extends StartupItem<C, M, C> {

  public StartupClass(int flags, C reference) {
    super(flags, reference);
  }

  public static <C, M> Builder<C, M> builder() {
    return new Builder<>();
  }

  public static Builder<DexType, DexMethod> dexBuilder() {
    return new Builder<>();
  }

  public static Builder<ClassReference, MethodReference> referenceBuilder() {
    return new Builder<>();
  }

  @Override
  public void accept(
      Consumer<StartupClass<C, M>> classConsumer, Consumer<StartupMethod<C, M>> methodConsumer) {
    classConsumer.accept(this);
  }

  @Override
  public <T> T apply(
      Function<StartupClass<C, M>, T> classFunction,
      Function<StartupMethod<C, M>, T> methodFunction) {
    return classFunction.apply(this);
  }

  @Override
  public boolean isStartupClass() {
    return true;
  }

  @Override
  public StartupClass<C, M> asStartupClass() {
    return this;
  }

  @Override
  public void serializeToString(
      StringBuilder builder,
      Function<C, String> classSerializer,
      Function<M, String> methodSerializer) {
    if (isSynthetic()) {
      builder.append('S');
    }
    builder.append(classSerializer.apply(getReference()));
  }

  public static class Builder<C, M> extends StartupItem.Builder<C, M, Builder<C, M>> {

    @Override
    public Builder<C, M> setMethodReference(M reference) {
      throw new Unreachable();
    }

    @Override
    public StartupClass<C, M> build() {
      return new StartupClass<>(flags, classReference);
    }
  }
}
