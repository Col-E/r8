// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.dex.Constants;
import com.google.common.collect.ImmutableList;
import javax.annotation.Nonnull;

import java.util.List;
import java.util.function.BooleanSupplier;

public class MethodAccessFlags extends AccessFlags<MethodAccessFlags> {

  private static final int SHARED_FLAGS
      = AccessFlags.BASE_FLAGS
      | Constants.ACC_SYNCHRONIZED
      | Constants.ACC_BRIDGE
      | Constants.ACC_VARARGS
      | Constants.ACC_NATIVE
      | Constants.ACC_ABSTRACT
      | Constants.ACC_STRICT;

  private static final int CF_FLAGS
      = SHARED_FLAGS;

  private static final int DEX_FLAGS
      = SHARED_FLAGS
      | Constants.ACC_CONSTRUCTOR
      | Constants.ACC_DECLARED_SYNCHRONIZED;

  @Override
  protected List<String> getNames() {
    return new ImmutableList.Builder<String>()
        .addAll(super.getNames())
        .add("synchronized")
        .add("bridge")
        .add("varargs")
        .add("native")
        .add("abstract")
        .add("strictfp")
        .build();
  }

  @Override
  protected List<BooleanSupplier> getPredicates() {
    return new ImmutableList.Builder<BooleanSupplier>()
        .addAll(super.getPredicates())
        .add(this::isSynchronized)
        .add(this::isBridge)
        .add(this::isVarargs)
        .add(this::isNative)
        .add(this::isAbstract)
        .add(this::isStrict)
        .build();
  }

  private MethodAccessFlags(int flags) {
    this(flags, flags);
  }

  private MethodAccessFlags(int originalFlags, int modifiedFlags) {
    super(originalFlags, modifiedFlags);
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean belongsToDirectPool() {
    return isStatic() || isPrivate() || isConstructor();
  }

  public boolean belongsToVirtualPool() {
    return !belongsToDirectPool();
  }

  @Nonnull
  @Override
  public MethodAccessFlags copy() {
    return new MethodAccessFlags(originalFlags, modifiedFlags);
  }

  @Override
  public MethodAccessFlags self() {
    return this;
  }

  public static MethodAccessFlags createForClassInitializer() {
    return fromSharedAccessFlags(Constants.ACC_STATIC | Constants.ACC_SYNTHETIC, true);
  }

  public static MethodAccessFlags createPublicStaticSynthetic() {
    return fromSharedAccessFlags(
        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC, false);
  }

  public static MethodAccessFlags fromSharedAccessFlags(int access, boolean isConstructor) {
    assert (access & SHARED_FLAGS) == access;
    assert CF_FLAGS == SHARED_FLAGS;
    return fromCfAccessFlags(access, isConstructor);
  }

  public static MethodAccessFlags fromCfAccessFlags(int access, boolean isConstructor) {
    return new MethodAccessFlags(
        (access & CF_FLAGS) | (isConstructor ? Constants.ACC_CONSTRUCTOR : 0));
  }

  public static MethodAccessFlags fromDexAccessFlags(int access) {
    MethodAccessFlags flags = new MethodAccessFlags(access & DEX_FLAGS);
    if (flags.isDeclaredSynchronized()) {
      flags.setSynchronized();
      flags.unsetDeclaredSynchronized();
    }
    return flags;
  }

  @Override
  public int getAsCfAccessFlags() {
    return materialize() & ~Constants.ACC_CONSTRUCTOR;
  }

  @Override
  public int getAsDexAccessFlags() {
    MethodAccessFlags copy = copy();
    if (copy.isSynchronized() && !copy.isNative()) {
      copy.unsetSynchronized();
      copy.setDeclaredSynchronized();
    }
    return copy.materialize();
  }

  @Override
  public MethodAccessFlags asMethodAccessFlags() {
    return this;
  }

  public boolean isSynchronized() {
    return isSet(Constants.ACC_SYNCHRONIZED);
  }

  public void setSynchronized() {
    set(Constants.ACC_SYNCHRONIZED);
  }

  public void demoteFromSynchronized() {
    demote(Constants.ACC_SYNCHRONIZED);
  }

  public void unsetSynchronized() {
    unset(Constants.ACC_SYNCHRONIZED);
  }

  public boolean isBridge() {
    return isSet(Constants.ACC_BRIDGE);
  }

  public void setBridge() {
    set(Constants.ACC_BRIDGE);
  }

  public void unsetBridge() {
    unset(Constants.ACC_BRIDGE);
  }

  public void demoteFromBridge() {
    demote(Constants.ACC_BRIDGE);
  }

  public boolean isVarargs() {
    return isSet(Constants.ACC_VARARGS);
  }

  public void setVarargs() {
    set(Constants.ACC_VARARGS);
  }

  public void unsetVarargs() {
    unset(Constants.ACC_VARARGS);
  }

  public boolean isNative() {
    return isSet(Constants.ACC_NATIVE);
  }

  public void setNative() {
    set(Constants.ACC_NATIVE);
  }

  public void unsetNative() {
    unset(Constants.ACC_NATIVE);
  }

  public boolean isAbstract() {
    return isSet(Constants.ACC_ABSTRACT);
  }

  public void setAbstract() {
    set(Constants.ACC_ABSTRACT);
  }

  public void demoteFromAbstract() {
    demote(Constants.ACC_ABSTRACT);
  }

  public void promoteToAbstract() {
    promote(Constants.ACC_ABSTRACT);
  }

  public void unsetAbstract() {
    unset(Constants.ACC_ABSTRACT);
  }

  public boolean isStrict() {
    return isSet(Constants.ACC_STRICT);
  }

  public void setStrict() {
    set(Constants.ACC_STRICT);
  }

  public void demoteFromStrict() {
    demote(Constants.ACC_STRICT);
  }

  public void unsetStrict() {
    unset(Constants.ACC_STRICT);
  }

  public boolean isConstructor() {
    return isSet(Constants.ACC_CONSTRUCTOR);
  }

  public void setConstructor() {
    set(Constants.ACC_CONSTRUCTOR);
  }

  public void setConstructor(DexMethod method, DexItemFactory dexItemFactory) {
    if (dexItemFactory.isConstructor(method) || dexItemFactory.isClassConstructor(method)) {
      setConstructor();
    }
  }

  public void unsetConstructor() {
    unset(Constants.ACC_CONSTRUCTOR);
  }

  // DEX only declared-synchronized flag.

  private boolean isDeclaredSynchronized() {
    return isSet(Constants.ACC_DECLARED_SYNCHRONIZED);
  }

  private void setDeclaredSynchronized() {
    set(Constants.ACC_DECLARED_SYNCHRONIZED);
  }

  public void unsetDeclaredSynchronized() {
    unset(Constants.ACC_DECLARED_SYNCHRONIZED);
  }

  public static class Builder extends BuilderBase<Builder, MethodAccessFlags> {

    Builder() {
      super(MethodAccessFlags.fromSharedAccessFlags(0, false));
    }

    public Builder set(int flag) {
      flags.set(flag);
      return this;
    }

    public Builder setAbstract() {
      flags.setAbstract();
      return this;
    }

    public Builder setBridge() {
      flags.setBridge();
      return this;
    }

    public Builder setConstructor() {
      flags.setConstructor();
      return this;
    }

    public Builder setStrict(boolean value) {
      if (value) {
        flags.setStrict();
      } else {
        flags.unsetStrict();
      }
      return this;
    }

    public Builder setSynchronized(boolean value) {
      if (value) {
        flags.setSynchronized();
      } else {
        flags.unsetSynchronized();
      }
      return this;
    }

    @Override
    public Builder self() {
      return this;
    }
  }
}
