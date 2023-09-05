// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.startup.profile;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.profile.AbstractProfileMethodRule;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.startup.StartupMethodBuilder;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.ThrowingConsumer;
import java.io.IOException;
import java.util.function.Function;

public class StartupProfileMethodRule extends StartupProfileRule
    implements AbstractProfileMethodRule {

  private final DexMethod method;

  StartupProfileMethodRule(DexMethod method) {
    this.method = method;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(DexItemFactory dexItemFactory) {
    return new Builder(dexItemFactory);
  }

  @Override
  public <E1 extends Exception, E2 extends Exception> void accept(
      ThrowingConsumer<? super StartupProfileClassRule, E1> classConsumer,
      ThrowingConsumer<? super StartupProfileMethodRule, E2> methodConsumer)
      throws E2 {
    methodConsumer.accept(this);
  }

  @Override
  public <T> T apply(
      Function<StartupProfileClassRule, T> classFunction,
      Function<StartupProfileMethodRule, T> methodFunction) {
    return methodFunction.apply(this);
  }

  @Override
  public DexMethod getReference() {
    return method;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    StartupProfileMethodRule that = (StartupProfileMethodRule) o;
    return method == that.method;
  }

  @Override
  public int hashCode() {
    return method.hashCode();
  }

  @Override
  public void write(Appendable appendable) throws IOException {
    appendable.append(method.toSmaliString());
  }

  public static class Builder
      implements AbstractProfileMethodRule.Builder<StartupProfileMethodRule, Builder>,
          StartupMethodBuilder {

    private final DexItemFactory dexItemFactory;

    private DexMethod method;

    Builder() {
      this(null);
    }

    Builder(DexItemFactory dexItemFactory) {
      this.dexItemFactory = dexItemFactory;
    }

    @Override
    public boolean isGreaterThanOrEqualTo(Builder builder) {
      return true;
    }

    @Override
    public Builder join(Builder builder) {
      return this;
    }

    @Override
    public Builder join(Builder builder, Runnable onChangedHandler) {
      return this;
    }

    @Override
    public Builder join(StartupProfileMethodRule methodRule) {
      return this;
    }

    @Override
    public Builder setIsStartup() {
      // Intentionally empty, startup profile rules do not have any flags.
      return this;
    }

    @Override
    public Builder setMethod(DexMethod method) {
      this.method = method;
      return this;
    }

    @Override
    public Builder setMethodReference(MethodReference classReference) {
      assert dexItemFactory != null;
      return setMethod(MethodReferenceUtils.toDexMethod(classReference, dexItemFactory));
    }

    @Override
    public StartupProfileMethodRule build() {
      return new StartupProfileMethodRule(method);
    }
  }
}
