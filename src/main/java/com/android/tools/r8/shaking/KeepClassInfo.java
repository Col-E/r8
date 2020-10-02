// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

/** Immutable keep requirements for a class. */
public final class KeepClassInfo extends KeepInfo<KeepClassInfo.Builder, KeepClassInfo> {

  // Requires all aspects of a class to be kept.
  private static final KeepClassInfo TOP = new Builder().makeTop().build();

  // Requires no aspects of a class to be kept.
  private static final KeepClassInfo BOTTOM = new Builder().makeBottom().build();

  public static KeepClassInfo top() {
    return TOP;
  }

  public static KeepClassInfo bottom() {
    return BOTTOM;
  }

  private KeepClassInfo(Builder builder) {
    super(builder);
  }

  @Override
  Builder builder() {
    return new Builder(this);
  }

  public Joiner joiner() {
    assert !isTop();
    return new Joiner(this);
  }

  @Override
  public boolean isRepackagingAllowed(GlobalKeepInfoConfiguration configuration) {
    return configuration.isRepackagingEnabled()
        && internalIsMinificationAllowed()
        && !internalIsAccessModificationRequiredForRepackaging();
  }

  @Override
  public boolean isTop() {
    return this.equals(top());
  }

  @Override
  public boolean isBottom() {
    return this.equals(bottom());
  }

  public static class Builder extends KeepInfo.Builder<Builder, KeepClassInfo> {

    private Builder() {
      super();
    }

    private Builder(KeepClassInfo original) {
      super(original);
    }

    @Override
    public KeepClassInfo getTopInfo() {
      return TOP;
    }

    @Override
    public KeepClassInfo getBottomInfo() {
      return BOTTOM;
    }

    @Override
    public Builder self() {
      return this;
    }

    @Override
    public boolean isEqualTo(KeepClassInfo other) {
      return true;
    }

    @Override
    public KeepClassInfo doBuild() {
      return new KeepClassInfo(this);
    }
  }

  public static class Joiner extends KeepInfo.Joiner<Joiner, Builder, KeepClassInfo> {

    public Joiner(KeepClassInfo info) {
      super(info.builder());
    }

    @Override
    Joiner self() {
      return this;
    }
  }
}
