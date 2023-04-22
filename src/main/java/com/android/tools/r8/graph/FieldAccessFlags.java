// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.dex.Constants;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.BooleanSupplier;

public class FieldAccessFlags extends AccessFlags<FieldAccessFlags> {

  private static final int FLAGS
      = AccessFlags.BASE_FLAGS
      | Constants.ACC_VOLATILE
      | Constants.ACC_TRANSIENT
      | Constants.ACC_ENUM;

  @Override
  protected List<String> getNames() {
    return new ImmutableList.Builder<String>()
        .addAll(super.getNames())
        .add("volatile")
        .add("transient")
        .add("enum")
        .build();
  }

  @Override
  protected List<BooleanSupplier> getPredicates() {
    return new ImmutableList.Builder<BooleanSupplier>()
        .addAll(super.getPredicates())
        .add(this::isVolatile)
        .add(this::isTransient)
        .add(this::isEnum)
        .build();
  }

  private FieldAccessFlags(int flags) {
    this(flags, flags);
  }

  private FieldAccessFlags(int originalFlags, int modifiedFlags) {
    super(originalFlags, modifiedFlags);
  }

  public static Builder builder() {
    return new Builder();
  }

  @NotNull
  @Override
  public FieldAccessFlags copy() {
    return new FieldAccessFlags(originalFlags, modifiedFlags);
  }

  @Override
  public FieldAccessFlags self() {
    return this;
  }

  public static FieldAccessFlags createPublicStaticFinalSynthetic() {
    return fromSharedAccessFlags(
        Constants.ACC_PUBLIC
            | Constants.ACC_STATIC
            | Constants.ACC_FINAL
            | Constants.ACC_SYNTHETIC);
  }

  public static FieldAccessFlags createPublicStaticSynthetic() {
    return fromSharedAccessFlags(
        Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC);
  }

  public static FieldAccessFlags createPrivateStaticSynthetic() {
    return fromSharedAccessFlags(
        Constants.ACC_PRIVATE | Constants.ACC_STATIC | Constants.ACC_SYNTHETIC);
  }

  public static FieldAccessFlags createPublicFinalSynthetic() {
    return fromSharedAccessFlags(
        Constants.ACC_PUBLIC | Constants.ACC_FINAL | Constants.ACC_SYNTHETIC);
  }

  public static FieldAccessFlags createPublicSynthetic() {
    return fromSharedAccessFlags(Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC);
  }

  public static FieldAccessFlags fromSharedAccessFlags(int access) {
    assert (access & FLAGS) == access;
    return new FieldAccessFlags(access & FLAGS);
  }

  public static FieldAccessFlags fromDexAccessFlags(int access) {
    return new FieldAccessFlags(access & FLAGS);
  }

  public static FieldAccessFlags fromCfAccessFlags(int access) {
    return new FieldAccessFlags(access & FLAGS);
  }

  @Override
  public FieldAccessFlags asFieldAccessFlags() {
    return this;
  }

  @Override
  public int getAsCfAccessFlags() {
    return materialize();
  }

  @Override
  public int getAsDexAccessFlags() {
    return materialize();
  }

  public boolean isVolatile() {
    return isSet(Constants.ACC_VOLATILE);
  }

  public void setVolatile() {
    set(Constants.ACC_VOLATILE);
  }

  public boolean isTransient() {
    return isSet(Constants.ACC_TRANSIENT);
  }

  public void setTransient() {
    set(Constants.ACC_TRANSIENT);
  }

  public boolean isEnum() {
    return isSet(Constants.ACC_ENUM);
  }

  public void setEnum() {
    set(Constants.ACC_ENUM);
  }

  public static class Builder extends BuilderBase<Builder, FieldAccessFlags> {

    public Builder() {
      super(FieldAccessFlags.fromSharedAccessFlags(0));
    }

    public Builder set(int flag) {
      flags.set(flag);
      return this;
    }

    @Override
    public Builder self() {
      return this;
    }
  }
}
