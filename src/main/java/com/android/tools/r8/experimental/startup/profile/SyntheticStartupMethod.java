// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup.profile;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.startup.SyntheticStartupMethodBuilder;
import com.android.tools.r8.utils.ClassReferenceUtils;
import java.util.function.Consumer;
import java.util.function.Function;

public class SyntheticStartupMethod extends StartupItem {

  private final DexType syntheticContextType;

  SyntheticStartupMethod(DexType syntheticContextType) {
    this.syntheticContextType = syntheticContextType;
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
    syntheticMethodConsumer.accept(this);
  }

  @Override
  public <T> T apply(
      Function<StartupClass, T> classFunction,
      Function<StartupMethod, T> methodFunction,
      Function<SyntheticStartupMethod, T> syntheticMethodFunction) {
    return syntheticMethodFunction.apply(this);
  }

  public DexType getSyntheticContextType() {
    return syntheticContextType;
  }

  @Override
  public boolean isSyntheticStartupMethod() {
    return true;
  }

  @Override
  public SyntheticStartupMethod asSyntheticStartupMethod() {
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
    SyntheticStartupMethod that = (SyntheticStartupMethod) o;
    return syntheticContextType == that.syntheticContextType;
  }

  @Override
  public int hashCode() {
    return syntheticContextType.hashCode();
  }

  @Override
  public String serializeToString() {
    return 'S' + syntheticContextType.toDescriptorString();
  }

  public static class Builder implements SyntheticStartupMethodBuilder {

    private final DexItemFactory dexItemFactory;

    private DexType syntheticContextReference;

    Builder() {
      this(null);
    }

    Builder(DexItemFactory dexItemFactory) {
      this.dexItemFactory = dexItemFactory;
    }

    @Override
    public Builder setSyntheticContextReference(ClassReference syntheticContextReference) {
      assert dexItemFactory != null;
      return setSyntheticContextReference(
          ClassReferenceUtils.toDexType(syntheticContextReference, dexItemFactory));
    }

    public Builder setSyntheticContextReference(DexType syntheticContextReference) {
      this.syntheticContextReference = syntheticContextReference;
      return this;
    }

    public SyntheticStartupMethod build() {
      return new SyntheticStartupMethod(syntheticContextReference);
    }
  }
}
