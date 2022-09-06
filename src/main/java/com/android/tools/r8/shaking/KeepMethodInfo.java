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
  private final boolean allowClosedWorldReasoning;
  private final boolean allowConstantArgumentOptimization;
  private final boolean allowInlining;
  private final boolean allowMethodStaticizing;
  private final boolean allowParameterRemoval;
  private final boolean allowParameterReordering;
  private final boolean allowParameterTypeStrengthening;
  private final boolean allowReturnTypeStrengthening;
  private final boolean allowUnusedArgumentOptimization;
  private final boolean allowUnusedReturnValueOptimization;

  private KeepMethodInfo(Builder builder) {
    super(builder);
    this.allowClassInlining = builder.isClassInliningAllowed();
    this.allowClosedWorldReasoning = builder.isClosedWorldReasoningAllowed();
    this.allowConstantArgumentOptimization = builder.isConstantArgumentOptimizationAllowed();
    this.allowInlining = builder.isInliningAllowed();
    this.allowMethodStaticizing = builder.isMethodStaticizingAllowed();
    this.allowParameterRemoval = builder.isParameterRemovalAllowed();
    this.allowParameterReordering = builder.isParameterReorderingAllowed();
    this.allowParameterTypeStrengthening = builder.isParameterTypeStrengtheningAllowed();
    this.allowReturnTypeStrengthening = builder.isReturnTypeStrengtheningAllowed();
    this.allowUnusedArgumentOptimization = builder.isUnusedArgumentOptimizationAllowed();
    this.allowUnusedReturnValueOptimization = builder.isUnusedReturnValueOptimizationAllowed();
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

  public boolean isClosedWorldReasoningAllowed(GlobalKeepInfoConfiguration configuration) {
    return isOptimizationAllowed(configuration) && internalIsClosedWorldReasoningAllowed();
  }

  boolean internalIsClosedWorldReasoningAllowed() {
    return allowClosedWorldReasoning;
  }

  public boolean isConstantArgumentOptimizationAllowed(GlobalKeepInfoConfiguration configuration) {
    return isOptimizationAllowed(configuration) && internalIsConstantArgumentOptimizationAllowed();
  }

  boolean internalIsConstantArgumentOptimizationAllowed() {
    return allowConstantArgumentOptimization;
  }

  public boolean isInliningAllowed(GlobalKeepInfoConfiguration configuration) {
    return isOptimizationAllowed(configuration) && internalIsInliningAllowed();
  }

  boolean internalIsInliningAllowed() {
    return allowInlining;
  }

  public boolean isMethodStaticizingAllowed(GlobalKeepInfoConfiguration configuration) {
    return isClosedWorldReasoningAllowed(configuration)
        && isOptimizationAllowed(configuration)
        && isShrinkingAllowed(configuration)
        && configuration.isMethodStaticizingEnabled()
        && internalIsMethodStaticizingAllowed();
  }

  boolean internalIsMethodStaticizingAllowed() {
    return allowMethodStaticizing;
  }

  public boolean isParameterRemovalAllowed(GlobalKeepInfoConfiguration configuration) {
    return isClosedWorldReasoningAllowed(configuration)
        && isOptimizationAllowed(configuration)
        && isShrinkingAllowed(configuration)
        && !isCheckDiscardedEnabled(configuration)
        && internalIsParameterRemovalAllowed();
  }

  boolean internalIsParameterRemovalAllowed() {
    return allowParameterRemoval;
  }

  public boolean isParameterReorderingAllowed(GlobalKeepInfoConfiguration configuration) {
    return isClosedWorldReasoningAllowed(configuration)
        && isOptimizationAllowed(configuration)
        && isShrinkingAllowed(configuration)
        && internalIsParameterReorderingAllowed();
  }

  boolean internalIsParameterReorderingAllowed() {
    return allowParameterReordering;
  }

  public boolean isParameterTypeStrengtheningAllowed(GlobalKeepInfoConfiguration configuration) {
    return isClosedWorldReasoningAllowed(configuration)
        && isOptimizationAllowed(configuration)
        && isShrinkingAllowed(configuration)
        && internalIsParameterTypeStrengtheningAllowed();
  }

  boolean internalIsParameterTypeStrengtheningAllowed() {
    return allowParameterTypeStrengthening;
  }

  public boolean isReturnTypeStrengtheningAllowed(GlobalKeepInfoConfiguration configuration) {
    return isClosedWorldReasoningAllowed(configuration)
        && isOptimizationAllowed(configuration)
        && isShrinkingAllowed(configuration)
        && internalIsReturnTypeStrengtheningAllowed();
  }

  boolean internalIsReturnTypeStrengtheningAllowed() {
    return allowReturnTypeStrengthening;
  }

  public boolean isUnusedArgumentOptimizationAllowed(GlobalKeepInfoConfiguration configuration) {
    return isClosedWorldReasoningAllowed(configuration)
        && isOptimizationAllowed(configuration)
        && isShrinkingAllowed(configuration)
        && internalIsUnusedArgumentOptimizationAllowed();
  }

  boolean internalIsUnusedArgumentOptimizationAllowed() {
    return allowUnusedArgumentOptimization;
  }

  public boolean isUnusedReturnValueOptimizationAllowed(GlobalKeepInfoConfiguration configuration) {
    return isClosedWorldReasoningAllowed(configuration)
        && isOptimizationAllowed(configuration)
        && isShrinkingAllowed(configuration)
        && internalIsUnusedReturnValueOptimizationAllowed();
  }

  boolean internalIsUnusedReturnValueOptimizationAllowed() {
    return allowUnusedReturnValueOptimization;
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
    private boolean allowClosedWorldReasoning;
    private boolean allowConstantArgumentOptimization;
    private boolean allowInlining;
    private boolean allowMethodStaticizing;
    private boolean allowParameterRemoval;
    private boolean allowParameterReordering;
    private boolean allowParameterTypeStrengthening;
    private boolean allowReturnTypeStrengthening;
    private boolean allowUnusedArgumentOptimization;
    private boolean allowUnusedReturnValueOptimization;

    private Builder() {
      super();
    }

    private Builder(KeepMethodInfo original) {
      super(original);
      allowClassInlining = original.internalIsClassInliningAllowed();
      allowClosedWorldReasoning = original.internalIsClosedWorldReasoningAllowed();
      allowConstantArgumentOptimization = original.internalIsConstantArgumentOptimizationAllowed();
      allowInlining = original.internalIsInliningAllowed();
      allowMethodStaticizing = original.internalIsMethodStaticizingAllowed();
      allowParameterRemoval = original.internalIsParameterRemovalAllowed();
      allowParameterReordering = original.internalIsParameterReorderingAllowed();
      allowParameterTypeStrengthening = original.internalIsParameterTypeStrengtheningAllowed();
      allowReturnTypeStrengthening = original.internalIsReturnTypeStrengtheningAllowed();
      allowUnusedArgumentOptimization = original.internalIsUnusedArgumentOptimizationAllowed();
      allowUnusedReturnValueOptimization =
          original.internalIsUnusedReturnValueOptimizationAllowed();
    }

    // Class inlining.

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

    // Closed world reasoning.

    public boolean isClosedWorldReasoningAllowed() {
      return allowClosedWorldReasoning;
    }

    public Builder setAllowClosedWorldReasoning(boolean allowClosedWorldReasoning) {
      this.allowClosedWorldReasoning = allowClosedWorldReasoning;
      return self();
    }

    public Builder allowClosedWorldReasoning() {
      return setAllowClosedWorldReasoning(true);
    }

    public Builder disallowClosedWorldReasoning() {
      return setAllowClosedWorldReasoning(false);
    }

    // Constant argument optimization.

    public boolean isConstantArgumentOptimizationAllowed() {
      return allowConstantArgumentOptimization;
    }

    public Builder setAllowConstantArgumentOptimization(boolean allowConstantArgumentOptimization) {
      this.allowConstantArgumentOptimization = allowConstantArgumentOptimization;
      return self();
    }

    public Builder allowConstantArgumentOptimization() {
      return setAllowConstantArgumentOptimization(true);
    }

    public Builder disallowConstantArgumentOptimization() {
      return setAllowConstantArgumentOptimization(false);
    }

    // Inlining.

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

    // Method staticizing.

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

    // Parameter removal.

    public boolean isParameterRemovalAllowed() {
      return allowParameterRemoval;
    }

    public Builder setAllowParameterRemoval(boolean allowParameterRemoval) {
      this.allowParameterRemoval = allowParameterRemoval;
      return self();
    }

    public Builder allowParameterRemoval() {
      return setAllowParameterRemoval(true);
    }

    public Builder disallowParameterRemoval() {
      return setAllowParameterRemoval(false);
    }

    // Parameter reordering.

    public boolean isParameterReorderingAllowed() {
      return allowParameterReordering;
    }

    public Builder setAllowParameterReordering(boolean allowParameterReordering) {
      this.allowParameterReordering = allowParameterReordering;
      return self();
    }

    public Builder allowParameterReordering() {
      return setAllowParameterReordering(true);
    }

    public Builder disallowParameterReordering() {
      return setAllowParameterReordering(false);
    }

    // Parameter type strengthening.

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

    // Return type strengthening.

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

    // Unused argument optimization.

    public boolean isUnusedArgumentOptimizationAllowed() {
      return allowUnusedArgumentOptimization;
    }

    public Builder setAllowUnusedArgumentOptimization(boolean allowUnusedArgumentOptimization) {
      this.allowUnusedArgumentOptimization = allowUnusedArgumentOptimization;
      return self();
    }

    public Builder allowUnusedArgumentOptimization() {
      return setAllowUnusedArgumentOptimization(true);
    }

    public Builder disallowUnusedArgumentOptimization() {
      return setAllowUnusedArgumentOptimization(false);
    }

    // Unused return value optimization.

    public boolean isUnusedReturnValueOptimizationAllowed() {
      return allowUnusedReturnValueOptimization;
    }

    public Builder setAllowUnusedReturnValueOptimization(
        boolean allowUnusedReturnValueOptimization) {
      this.allowUnusedReturnValueOptimization = allowUnusedReturnValueOptimization;
      return self();
    }

    public Builder allowUnusedReturnValueOptimization() {
      return setAllowUnusedReturnValueOptimization(true);
    }

    public Builder disallowUnusedReturnValueOptimization() {
      return setAllowUnusedReturnValueOptimization(false);
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
          && isClosedWorldReasoningAllowed() == other.internalIsClosedWorldReasoningAllowed()
          && isConstantArgumentOptimizationAllowed()
              == other.internalIsConstantArgumentOptimizationAllowed()
          && isInliningAllowed() == other.internalIsInliningAllowed()
          && isMethodStaticizingAllowed() == other.internalIsMethodStaticizingAllowed()
          && isParameterRemovalAllowed() == other.internalIsParameterRemovalAllowed()
          && isParameterReorderingAllowed() == other.internalIsParameterReorderingAllowed()
          && isParameterTypeStrengtheningAllowed()
              == other.internalIsParameterTypeStrengtheningAllowed()
          && isReturnTypeStrengtheningAllowed() == other.internalIsReturnTypeStrengtheningAllowed()
          && isUnusedArgumentOptimizationAllowed()
              == other.internalIsUnusedArgumentOptimizationAllowed()
          && isUnusedReturnValueOptimizationAllowed()
              == other.internalIsUnusedReturnValueOptimizationAllowed();
    }

    @Override
    public KeepMethodInfo doBuild() {
      return new KeepMethodInfo(this);
    }

    @Override
    public Builder makeTop() {
      return super.makeTop()
          .disallowClassInlining()
          .disallowClosedWorldReasoning()
          .disallowConstantArgumentOptimization()
          .disallowInlining()
          .disallowMethodStaticizing()
          .disallowParameterRemoval()
          .disallowParameterReordering()
          .disallowParameterTypeStrengthening()
          .disallowReturnTypeStrengthening()
          .disallowUnusedArgumentOptimization()
          .disallowUnusedReturnValueOptimization();
    }

    @Override
    public Builder makeBottom() {
      return super.makeBottom()
          .allowClassInlining()
          .allowClosedWorldReasoning()
          .allowConstantArgumentOptimization()
          .allowInlining()
          .allowMethodStaticizing()
          .allowParameterRemoval()
          .allowParameterReordering()
          .allowParameterTypeStrengthening()
          .allowReturnTypeStrengthening()
          .allowUnusedArgumentOptimization()
          .allowUnusedReturnValueOptimization();
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

    public Joiner disallowClosedWorldReasoning() {
      builder.disallowClosedWorldReasoning();
      return self();
    }

    public Joiner disallowConstantArgumentOptimization() {
      builder.disallowConstantArgumentOptimization();
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

    public Joiner disallowParameterRemoval() {
      builder.disallowParameterRemoval();
      return self();
    }

    public Joiner disallowParameterReordering() {
      builder.disallowParameterReordering();
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

    public Joiner disallowUnusedArgumentOptimization() {
      builder.disallowUnusedArgumentOptimization();
      return self();
    }

    public Joiner disallowUnusedReturnValueOptimization() {
      builder.disallowUnusedReturnValueOptimization();
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
          .applyIf(
              !joiner.builder.isClosedWorldReasoningAllowed(), Joiner::disallowClosedWorldReasoning)
          .applyIf(
              !joiner.builder.isConstantArgumentOptimizationAllowed(),
              Joiner::disallowConstantArgumentOptimization)
          .applyIf(!joiner.builder.isInliningAllowed(), Joiner::disallowInlining)
          .applyIf(!joiner.builder.isMethodStaticizingAllowed(), Joiner::disallowMethodStaticizing)
          .applyIf(!joiner.builder.isParameterRemovalAllowed(), Joiner::disallowParameterRemoval)
          .applyIf(
              !joiner.builder.isParameterReorderingAllowed(), Joiner::disallowParameterReordering)
          .applyIf(
              !joiner.builder.isParameterTypeStrengtheningAllowed(),
              Joiner::disallowParameterTypeStrengthening)
          .applyIf(
              !joiner.builder.isReturnTypeStrengtheningAllowed(),
              Joiner::disallowReturnTypeStrengthening)
          .applyIf(
              !joiner.builder.isUnusedArgumentOptimizationAllowed(),
              Joiner::disallowUnusedArgumentOptimization)
          .applyIf(
              !joiner.builder.isUnusedReturnValueOptimizationAllowed(),
              Joiner::disallowUnusedReturnValueOptimization);
    }

    @Override
    Joiner self() {
      return this;
    }
  }
}
