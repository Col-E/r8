// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.startup.profile;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.profile.AbstractProfileClassRule;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.startup.StartupClassBuilder;
import com.android.tools.r8.utils.ClassReferenceUtils;
import com.android.tools.r8.utils.ThrowingConsumer;
import java.io.IOException;
import java.util.function.Function;

public class StartupProfileClassRule extends StartupProfileRule
    implements AbstractProfileClassRule {

  private final DexType type;

  StartupProfileClassRule(DexType type) {
    this.type = type;
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
      throws E1 {
    classConsumer.accept(this);
  }

  @Override
  public <T> T apply(
      Function<StartupProfileClassRule, T> classFunction,
      Function<StartupProfileMethodRule, T> methodFunction) {
    return classFunction.apply(this);
  }

  @Override
  public DexType getReference() {
    return type;
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
    StartupProfileClassRule that = (StartupProfileClassRule) o;
    return type == that.type;
  }

  @Override
  public int hashCode() {
    return type.hashCode();
  }

  @Override
  public void write(Appendable appendable) throws IOException {
    appendable.append(getReference().toDescriptorString());
  }

  public static class Builder
      implements AbstractProfileClassRule.Builder<StartupProfileClassRule>, StartupClassBuilder {

    private final DexItemFactory dexItemFactory;

    private DexType type;

    Builder() {
      this(null);
    }

    Builder(DexItemFactory dexItemFactory) {
      this.dexItemFactory = dexItemFactory;
    }

    @Override
    public Builder setClassReference(ClassReference classReference) {
      assert dexItemFactory != null;
      return setClassReference(ClassReferenceUtils.toDexType(classReference, dexItemFactory));
    }

    public Builder setClassReference(DexType type) {
      this.type = type;
      return this;
    }

    @Override
    public StartupProfileClassRule build() {
      return new StartupProfileClassRule(type);
    }
  }
}
