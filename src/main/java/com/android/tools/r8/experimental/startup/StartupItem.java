// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class StartupItem<C, M, R> {

  private static final int FLAG_SYNTHETIC = 1;

  protected final int flags;
  protected final R reference;

  public StartupItem(int flags, R reference) {
    this.flags = flags;
    this.reference = reference;
  }

  public abstract void accept(
      Consumer<StartupClass<C, M>> classConsumer, Consumer<StartupMethod<C, M>> methodConsumer);

  public abstract <T> T apply(
      Function<StartupClass<C, M>, T> classFunction,
      Function<StartupMethod<C, M>, T> methodFunction);

  public boolean isStartupClass() {
    return false;
  }

  public StartupClass<C, M> asStartupClass() {
    return null;
  }

  public boolean isStartupMethod() {
    return false;
  }

  public StartupMethod<C, M> asStartupMethod() {
    return null;
  }

  public static <C, M> Builder<C, M, ?> builder() {
    return new Builder<>();
  }

  public static Builder<DexType, DexMethod, ?> dexBuilder() {
    return new Builder<>();
  }

  public int getFlags() {
    return flags;
  }

  public R getReference() {
    return reference;
  }

  public boolean isSynthetic() {
    return (flags & FLAG_SYNTHETIC) != 0;
  }

  public abstract void serializeToString(
      StringBuilder builder,
      Function<C, String> classSerializer,
      Function<M, String> methodSerializer);

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    StartupItem<?, ?, ?> startupItem = (StartupItem<?, ?, ?>) obj;
    return flags == startupItem.flags && reference.equals(startupItem.reference);
  }

  @Override
  public int hashCode() {
    return (reference.hashCode() << 1) | flags;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    if (isSynthetic()) {
      builder.append('S');
    }
    builder.append(reference);
    return builder.toString();
  }

  public static class Builder<C, M, B extends Builder<C, M, B>> {

    protected int flags;
    protected C classReference;
    protected M methodReference;

    public B applyIf(boolean condition, Consumer<B> thenConsumer, Consumer<B> elseConsumer) {
      if (condition) {
        thenConsumer.accept(self());
      } else {
        elseConsumer.accept(self());
      }
      return self();
    }

    public B setFlags(int flags) {
      this.flags = flags;
      return self();
    }

    public B setClassReference(C reference) {
      this.classReference = reference;
      return self();
    }

    public B setMethodReference(M reference) {
      this.methodReference = reference;
      return self();
    }

    public B setSynthetic() {
      this.flags |= FLAG_SYNTHETIC;
      return self();
    }

    public B setSynthetic(boolean synthetic) {
      if (synthetic) {
        return setSynthetic();
      }
      assert (flags & FLAG_SYNTHETIC) == 0;
      return self();
    }

    public StartupItem<C, M, ?> build() {
      if (classReference != null) {
        return buildStartupClass();
      } else {
        return buildStartupMethod();
      }
    }

    public StartupClass<C, M> buildStartupClass() {
      assert classReference != null;
      return new StartupClass<>(flags, classReference);
    }

    public StartupMethod<C, M> buildStartupMethod() {
      assert methodReference != null;
      return new StartupMethod<>(flags, methodReference);
    }

    @SuppressWarnings("unchecked")
    public B self() {
      return (B) this;
    }
  }
}
