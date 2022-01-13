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

  private final boolean allowClassInlining;
  private final boolean allowInlining;
  private final boolean allowMethodStaticizing;
  private final boolean allowParameterTypeStrengthening;
  private final boolean allowReturnTypeStrengthening;

  private KeepMethodInfo(Builder builder) {
    super(builder);
    this.allowClassInlining = builder.isClassInliningAllowed();
    this.allowInlining = builder.isInliningAllowed();
    this.allowMethodStaticizing = builder.isMethodStaticizingAllowed();
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

  public boolean isClassInliningAllowed(GlobalKeepInfoConfiguration configuration) {
    return isOptimizationAllowed(configuration) && internalIsClassInliningAllowed();
  }

  boolean internalIsClassInliningAllowed() {
    return allowClassInlining;
  }

  public boolean isInliningAllowed(GlobalKeepInfoConfiguration configuration) {
    return isOptimizationAllowed(configuration) && internalIsInliningAllowed();
  }

  boolean internalIsInliningAllowed() {
    return allowInlining;
  }

  public boolean isMethodStaticizingAllowed(GlobalKeepInfoConfiguration configuration) {
    return isOptimizationAllowed(configuration)
        && isShrinkingAllowed(configuration)
        && configuration.isMethodStaticizingEnabled()
        && internalIsMethodStaticizingAllowed();
  }

  boolean internalIsMethodStaticizingAllowed() {
    return allowMethodStaticizing;
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

  public static class Builder extends KeepInfo.Builder<Builder, KeepMethodInfo> {

    private boolean allowClassInlining;
    private boolean allowInlining;
    private boolean allowMethodStaticizing;
    private boolean allowParameterTypeStrengthening;
    private boolean allowReturnTypeStrengthening;

    private Builder() {
      super();
    }

    private Builder(KeepMethodInfo original) {
      super(original);
      allowClassInlining = original.internalIsClassInliningAllowed();
      allowInlining = original.internalIsInliningAllowed();
      allowMethodStaticizing = original.internalIsMethodStaticizingAllowed();
      allowParameterTypeStrengthening = original.internalIsParameterTypeStrengtheningAllowed();
      allowReturnTypeStrengthening = original.internalIsReturnTypeStrengtheningAllowed();
    }

    public boolean isClassInliningAllowed() {
      return allowClassInlining;
    }

    public Builder setAllowClassInlining(boolean allowClassInlining) {
      this.allowClassInlining = allowClassInlining;
      return self();
    }

    public Builder allowClassInlining() {
      return setAllowClassInlining(true);
    }

    public Builder disallowClassInlining() {
      return setAllowClassInlining(false);
    }

    public boolean isInliningAllowed() {
      return allowInlining;
    }

    public Builder setAllowInlining(boolean allowInlining) {
      this.allowInlining = allowInlining;
      return self();
    }

    public Builder allowInlining() {
      return setAllowInlining(true);
    }

    public Builder disallowInlining() {
      return setAllowInlining(false);
    }

    public boolean isMethodStaticizingAllowed() {
      return allowMethodStaticizing;
    }

    public Builder setAllowMethodStaticizing(boolean allowMethodStaticizing) {
      this.allowMethodStaticizing = allowMethodStaticizing;
      return self();
    }

    public Builder allowMethodStaticizing() {
      return setAllowMethodStaticizing(true);
    }

    public Builder disallowMethodStaticizing() {
      return setAllowMethodStaticizing(false);
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
          && isClassInliningAllowed() == other.internalIsClassInliningAllowed()
          && isInliningAllowed() == other.internalIsInliningAllowed()
          && isMethodStaticizingAllowed() == other.internalIsMethodStaticizingAllowed()
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
      return super.makeTop()
          .disallowClassInlining()
          .disallowInlining()
          .disallowMethodStaticizing()
          .disallowParameterTypeStrengthening()
          .disallowReturnTypeStrengthening();
    }

    @Override
    public Builder makeBottom() {
      return super.makeBottom()
          .allowClassInlining()
          .allowInlining()
          .allowMethodStaticizing()
          .allowParameterTypeStrengthening()
          .allowReturnTypeStrengthening();
    }
  }

  public static class Joiner extends KeepInfo.Joiner<Joiner, Builder, KeepMethodInfo> {

    public Joiner(KeepMethodInfo info) {
      super(info.builder());
    }

    public Joiner disallowClassInlining() {
      builder.disallowClassInlining();
      return self();
    }

    public Joiner disallowInlining() {
      builder.disallowInlining();
      return self();
    }

    public Joiner disallowMethodStaticizing() {
      builder.disallowMethodStaticizing();
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
          .applyIf(!joiner.builder.isClassInliningAllowed(), Joiner::disallowClassInlining)
          .applyIf(!joiner.builder.isInliningAllowed(), Joiner::disallowInlining)
          .applyIf(!joiner.builder.isMethodStaticizingAllowed(), Joiner::disallowMethodStaticizing)
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
