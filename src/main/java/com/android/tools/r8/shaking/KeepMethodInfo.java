// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

/** Immutable keep requirements for a method. */
public final class KeepMethodInfo extends KeepMemberInfo<KeepMethodInfo.Builder, KeepMethodInfo> {

  // Requires all aspects of a method to be kept.
  private static final KeepMethodInfo TOP = new Builder().makeTop().build();

  // Requires no aspects of a method to be kept.
  private static final KeepMethodInfo BOTTOM = new Builder().makeBottom().build();

  public static KeepMethodInfo top() {
    return TOP;
  }

  public static KeepMethodInfo bottom() {
    return BOTTOM;
  }

  public static Joiner newEmptyJoiner() {
    return bottom().joiner();
  }

  private KeepMethodInfo(Builder builder) {
    super(builder);
  }

  // This builder is not private as there are known instances where it is safe to modify keep info
  // in a non-upwards direction.
  @Override
  Builder builder() {
    return new Builder(this);
  }

  public boolean isArgumentPropagationAllowed(GlobalKeepInfoConfiguration configuration) {
    return isOptimizationAllowed(configuration);
  }

  public Joiner joiner() {
    assert !isTop();
    return new Joiner(this);
  }

  @Override
  public boolean isTop() {
    return this.equals(top());
  }

  @Override
  public boolean isBottom() {
    return this.equals(bottom());
  }

  public static class Builder extends KeepInfo.Builder<Builder, KeepMethodInfo> {

    private Builder() {
      super();
    }

    private Builder(KeepMethodInfo original) {
      super(original);
    }

    @Override
    public Builder self() {
      return this;
    }

    @Override
    public KeepMethodInfo getTopInfo() {
      return TOP;
    }

    @Override
    public KeepMethodInfo getBottomInfo() {
      return BOTTOM;
    }

    @Override
    public boolean isEqualTo(KeepMethodInfo other) {
      return internalIsEqualTo(other);
    }

    @Override
    public KeepMethodInfo doBuild() {
      return new KeepMethodInfo(this);
    }
  }

  public static class Joiner extends KeepInfo.Joiner<Joiner, Builder, KeepMethodInfo> {

    public Joiner(KeepMethodInfo info) {
      super(info.builder());
    }

    @Override
    public Joiner asMethodJoiner() {
      return this;
    }

    @Override
    public Joiner merge(Joiner joiner) {
      // Should be extended to merge the fields of this class in case any are added.
      return super.merge(joiner);
    }

    @Override
    Joiner self() {
      return this;
    }
  }
}
