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

  private final boolean allowParameterTypeStrengthening;
  private final boolean allowReturnTypeStrengthening;

  private KeepMethodInfo(Builder builder) {
    super(builder);
    this.allowParameterTypeStrengthening = builder.isParameterTypeStrengtheningAllowed();
    this.allowReturnTypeStrengthening = builder.isReturnTypeStrengtheningAllowed();
  }

  // This builder is not private as there are known instances where it is safe to modify keep info
  // in a non-upwards direction.
  @Override
  Builder builder() {
    return new Builder(this);
  }

  public boolean isArgumentPropagationAllowed(GlobalKeepInfoConfiguration configuration) {
    return isParameterRemovalAllowed(configuration);
  }

  public boolean isParameterTypeStrengtheningAllowed(GlobalKeepInfoConfiguration configuration) {
    return isOptimizationAllowed(configuration)
        && isShrinkingAllowed(configuration)
        && internalIsParameterTypeStrengtheningAllowed();
  }

  boolean internalIsParameterTypeStrengtheningAllowed() {
    return allowParameterTypeStrengthening;
  }

  public boolean isReturnTypeStrengtheningAllowed(GlobalKeepInfoConfiguration configuration) {
    return isOptimizationAllowed(configuration)
        && isShrinkingAllowed(configuration)
        && internalIsReturnTypeStrengtheningAllowed();
  }

  boolean internalIsReturnTypeStrengtheningAllowed() {
    return allowReturnTypeStrengthening;
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

  public boolean isInliningAllowed(GlobalKeepInfoConfiguration configuration) {
    return isOptimizationAllowed(configuration);
  }

  public static class Builder extends KeepInfo.Builder<Builder, KeepMethodInfo> {

    private boolean allowParameterTypeStrengthening;
    private boolean allowReturnTypeStrengthening;

    private Builder() {
      super();
    }

    private Builder(KeepMethodInfo original) {
      super(original);
      allowParameterTypeStrengthening = original.internalIsParameterTypeStrengtheningAllowed();
      allowReturnTypeStrengthening = original.internalIsReturnTypeStrengtheningAllowed();
    }

    public boolean isParameterTypeStrengtheningAllowed() {
      return allowParameterTypeStrengthening;
    }

    public Builder setAllowParameterTypeStrengthening(boolean allowParameterTypeStrengthening) {
      this.allowParameterTypeStrengthening = allowParameterTypeStrengthening;
      return self();
    }

    public Builder allowParameterTypeStrengthening() {
      return setAllowParameterTypeStrengthening(true);
    }

    public Builder disallowParameterTypeStrengthening() {
      return setAllowParameterTypeStrengthening(false);
    }

    public boolean isReturnTypeStrengtheningAllowed() {
      return allowReturnTypeStrengthening;
    }

    public Builder setAllowReturnTypeStrengthening(boolean allowReturnTypeStrengthening) {
      this.allowReturnTypeStrengthening = allowReturnTypeStrengthening;
      return self();
    }

    public Builder allowReturnTypeStrengthening() {
      return setAllowReturnTypeStrengthening(true);
    }

    public Builder disallowReturnTypeStrengthening() {
      return setAllowReturnTypeStrengthening(false);
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
    boolean internalIsEqualTo(KeepMethodInfo other) {
      return super.internalIsEqualTo(other)
          && isParameterTypeStrengtheningAllowed()
              == other.internalIsParameterTypeStrengtheningAllowed()
          && isReturnTypeStrengtheningAllowed() == other.internalIsReturnTypeStrengtheningAllowed();
    }

    @Override
    public KeepMethodInfo doBuild() {
      return new KeepMethodInfo(this);
    }

    @Override
    public Builder makeTop() {
      return super.makeTop().disallowParameterTypeStrengthening().disallowReturnTypeStrengthening();
    }

    @Override
    public Builder makeBottom() {
      return super.makeBottom().allowParameterTypeStrengthening().allowReturnTypeStrengthening();
    }
  }

  public static class Joiner extends KeepInfo.Joiner<Joiner, Builder, KeepMethodInfo> {

    public Joiner(KeepMethodInfo info) {
      super(info.builder());
    }

    public Joiner disallowInlining() {
      return self();
    }

    public Joiner disallowParameterTypeStrengthening() {
      builder.disallowParameterTypeStrengthening();
      return self();
    }

    public Joiner disallowReturnTypeStrengthening() {
      builder.disallowReturnTypeStrengthening();
      return self();
    }

    @Override
    public Joiner asMethodJoiner() {
      return this;
    }

    @Override
    public Joiner merge(Joiner joiner) {
      // Should be extended to merge the fields of this class in case any are added.
      return super.merge(joiner)
          .applyIf(
              !joiner.builder.isParameterTypeStrengtheningAllowed(),
              Joiner::disallowParameterTypeStrengthening)
          .applyIf(
              !joiner.builder.isReturnTypeStrengtheningAllowed(),
              Joiner::disallowReturnTypeStrengthening);
    }

    @Override
    Joiner self() {
      return this;
    }
  }
}
