// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup.profile;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.startup.StartupMethodBuilder;
import com.android.tools.r8.utils.MethodReferenceUtils;
import java.util.function.Consumer;
import java.util.function.Function;

public class StartupMethod extends StartupItem {

  private final DexMethod method;

  StartupMethod(DexMethod method) {
    this.method = method;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(DexItemFactory dexItemFactory) {
    return new Builder(dexItemFactory);
  }

  @Override
  public void accept(
      Consumer<StartupClass> classConsumer,
      Consumer<StartupMethod> methodConsumer,
      Consumer<SyntheticStartupMethod> syntheticMethodConsumer) {
    methodConsumer.accept(this);
  }

  @Override
  public <T> T apply(
      Function<StartupClass, T> classFunction,
      Function<StartupMethod, T> methodFunction,
      Function<SyntheticStartupMethod, T> syntheticMethodFunction) {
    return methodFunction.apply(this);
  }

  public DexMethod getReference() {
    return method;
  }

  @Override
  public boolean isStartupMethod() {
    return true;
  }

  @Override
  public StartupMethod asStartupMethod() {
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    StartupMethod that = (StartupMethod) o;
    return method == that.method;
  }

  @Override
  public int hashCode() {
    return method.hashCode();
  }

  @Override
  public String serializeToString() {
    return method.toSmaliString();
  }

  public static class Builder implements StartupMethodBuilder {

    private final DexItemFactory dexItemFactory;

    private DexMethod method;

    Builder() {
      this(null);
    }

    Builder(DexItemFactory dexItemFactory) {
      this.dexItemFactory = dexItemFactory;
    }

    @Override
    public Builder setMethodReference(MethodReference classReference) {
      assert dexItemFactory != null;
      return setMethodReference(MethodReferenceUtils.toDexMethod(classReference, dexItemFactory));
    }

    public Builder setMethodReference(DexMethod method) {
      this.method = method;
      return this;
    }

    public StartupMethod build() {
      return new StartupMethod(method);
    }
  }
}
