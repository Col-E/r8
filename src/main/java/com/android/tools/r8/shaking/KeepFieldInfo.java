// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

/** Immutable keep requirements for a field. */
public final class KeepFieldInfo extends KeepMemberInfo<KeepFieldInfo.Builder, KeepFieldInfo> {

  // Requires all aspects of a field to be kept.
  private static final KeepFieldInfo TOP = new Builder().makeTop().build();

  // Requires no aspects of a field to be kept.
  private static final KeepFieldInfo BOTTOM = new Builder().makeBottom().build();

  public static KeepFieldInfo top() {
    return TOP;
  }

  public static KeepFieldInfo bottom() {
    return BOTTOM;
  }

  private KeepFieldInfo(Builder builder) {
    super(builder);
  }

  // This builder is not private as there are known instances where it is safe to modify keep info
  // in a non-upwards direction.
  @Override
  Builder builder() {
    return new Builder(this);
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

  public static class Builder extends KeepInfo.Builder<Builder, KeepFieldInfo> {

    private Builder() {
      super();
    }

    private Builder(KeepFieldInfo original) {
      super(original);
    }

    @Override
    public KeepFieldInfo getTopInfo() {
      return TOP;
    }

    @Override
    public KeepFieldInfo getBottomInfo() {
      return BOTTOM;
    }

    @Override
    public Builder self() {
      return this;
    }

    @Override
    public boolean isEqualTo(KeepFieldInfo other) {
      return true;
    }

    @Override
    public KeepFieldInfo doBuild() {
      return new KeepFieldInfo(this);
    }
  }

  public static class Joiner extends KeepInfo.Joiner<Joiner, Builder, KeepFieldInfo> {

    public Joiner(KeepFieldInfo info) {
      super(info.builder());
    }

    @Override
    Joiner self() {
      return this;
    }
  }
}
