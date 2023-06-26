// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.EnclosingMethodAttribute;
import com.android.tools.r8.shaking.KeepInfo.Builder;
import com.android.tools.r8.shaking.KeepReason.ReflectiveUseFrom;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.Sets;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/** Keep information that can be associated with any item, i.e., class, method or field. */
public abstract class KeepInfo<B extends Builder<B, K>, K extends KeepInfo<B, K>> {

  private final boolean allowAccessModification;
  private final boolean allowAccessModificationForTesting;
  private final boolean allowAnnotationRemoval;
  private final boolean allowMinification;
  private final boolean allowOptimization;
  private final boolean allowShrinking;
  private final boolean allowSignatureRemoval;
  private final boolean checkDiscarded;

  private KeepInfo(
      boolean allowAccessModification,
      boolean allowAccessModificationForTesting,
      boolean allowAnnotationRemoval,
      boolean allowMinification,
      boolean allowOptimization,
      boolean allowShrinking,
      boolean allowSignatureRemoval,
      boolean checkDiscarded) {
    this.allowAccessModification = allowAccessModification;
    this.allowAccessModificationForTesting = allowAccessModificationForTesting;
    this.allowAnnotationRemoval = allowAnnotationRemoval;
    this.allowMinification = allowMinification;
    this.allowOptimization = allowOptimization;
    this.allowShrinking = allowShrinking;
    this.allowSignatureRemoval = allowSignatureRemoval;
    this.checkDiscarded = checkDiscarded;
  }

  KeepInfo(B builder) {
    this(
        builder.isAccessModificationAllowed(),
        builder.isAccessModificationAllowedForTesting(),
        builder.isAnnotationRemovalAllowed(),
        builder.isMinificationAllowed(),
        builder.isOptimizationAllowed(),
        builder.isShrinkingAllowed(),
        builder.isSignatureRemovalAllowed(),
        builder.isCheckDiscardedEnabled());
  }

  public static Joiner<?, ?, ?> newEmptyJoinerFor(DexReference reference) {
    return reference.apply(
        clazz -> KeepClassInfo.newEmptyJoiner(),
        field -> KeepFieldInfo.newEmptyJoiner(),
        method -> KeepMethodInfo.newEmptyJoiner());
  }

  abstract B builder();

  /**
   * True if an item may have all of its annotations removed.
   *
   * <p>If this returns false, some annotations may still be removed if the configuration does not
   * keep all annotation attributes.
   */
  public boolean isAnnotationRemovalAllowed(GlobalKeepInfoConfiguration configuration) {
    return configuration.isAnnotationRemovalEnabled() && internalIsAnnotationRemovalAllowed();
  }

  boolean internalIsAnnotationRemovalAllowed() {
    return allowAnnotationRemoval;
  }

  public boolean isCheckDiscardedEnabled(GlobalKeepInfoConfiguration configuration) {
    return internalIsCheckDiscardedEnabled();
  }

  boolean internalIsCheckDiscardedEnabled() {
    return checkDiscarded;
  }

  /**
   * True if an item must be present in the output.
   *
   * @deprecated Prefer task dependent predicates.
   */
  @Deprecated
  public boolean isPinned(GlobalKeepInfoConfiguration configuration) {
    return !isOptimizationAllowed(configuration) || !isShrinkingAllowed(configuration);
  }

  /**
   * True if an item may have its name minified/changed.
   *
   * <p>This method requires knowledge of the global configuration as that can override the concrete
   * value on a given item.
   */
  public boolean isMinificationAllowed(GlobalKeepInfoConfiguration configuration) {
    return configuration.isMinificationEnabled() && internalIsMinificationAllowed();
  }

  boolean internalIsMinificationAllowed() {
    return allowMinification;
  }

  /**
   * True if an item may be optimized (i.e., the item is not soft pinned).
   *
   * <p>This method requires knowledge of the global configuration as that can override the concrete
   * value on a given item.
   */
  public boolean isOptimizationAllowed(GlobalKeepInfoConfiguration configuration) {
    return configuration.isOptimizationEnabled() && internalIsOptimizationAllowed();
  }

  boolean internalIsOptimizationAllowed() {
    return allowOptimization;
  }

  /**
   * True if an item is subject to shrinking (i.e., tree shaking).
   *
   * <p>This method requires knowledge of the global configuration as that can override the concrete
   * value on a given item.
   */
  public boolean isShrinkingAllowed(GlobalKeepInfoConfiguration configuration) {
    return configuration.isTreeShakingEnabled() && internalIsShrinkingAllowed();
  }

  boolean internalIsShrinkingAllowed() {
    return allowShrinking;
  }

  /**
   * True if an item may have its generic signature removed.
   *
   * <p>This method requires knowledge of the global configuration as that can override the concrete
   * value on a given item.
   */
  public boolean isSignatureRemovalAllowed(GlobalKeepInfoConfiguration configuration) {
    if (!configuration.isKeepAttributesSignatureEnabled()) {
      return true;
    }
    return !configuration.isForceProguardCompatibilityEnabled()
        && internalIsSignatureRemovalAllowed();
  }

  boolean internalIsSignatureRemovalAllowed() {
    return allowSignatureRemoval;
  }

  /**
   * True if an item may have its access flags modified.
   *
   * <p>This method requires knowledge of the global access modification as that will override the
   * concrete value on a given item.
   *
   * @param configuration Global configuration object to determine access modification.
   */
  public boolean isAccessModificationAllowed(GlobalKeepInfoConfiguration configuration) {
    return configuration.isAccessModificationEnabled() && internalIsAccessModificationAllowed();
  }

  // Internal accessor for the items access-modification bit.
  boolean internalIsAccessModificationAllowed() {
    return allowAccessModification;
  }

  public boolean isAccessModificationAllowedForTesting(GlobalKeepInfoConfiguration configuration) {
    return internalIsAccessModificationAllowedForTesting();
  }

  // Internal accessor for the items access-modification bit.
  boolean internalIsAccessModificationAllowedForTesting() {
    return allowAccessModificationForTesting;
  }

  public boolean isEnclosingMethodAttributeRemovalAllowed(
      GlobalKeepInfoConfiguration configuration,
      EnclosingMethodAttribute enclosingMethodAttribute,
      AppView<AppInfoWithLiveness> appView) {
    if (!configuration.isKeepEnclosingMethodAttributeEnabled()) {
      return true;
    }
    if (configuration.isForceProguardCompatibilityEnabled()) {
      return false;
    }
    return !isPinned(configuration) || !enclosingMethodAttribute.isEnclosingPinned(appView);
  }

  public boolean isInnerClassesAttributeRemovalAllowed(GlobalKeepInfoConfiguration configuration) {
    if (!configuration.isKeepInnerClassesAttributeEnabled()) {
      return true;
    }
    return !(configuration.isForceProguardCompatibilityEnabled() || isPinned(configuration));
  }

  public boolean isInnerClassesAttributeRemovalAllowed(
      GlobalKeepInfoConfiguration configuration,
      EnclosingMethodAttribute enclosingMethodAttribute) {
    if (!configuration.isKeepInnerClassesAttributeEnabled()) {
      return true;
    }
    if (configuration.isForceProguardCompatibilityEnabled()) {
      return false;
    }
    // The inner class is dependent on the enclosingMethodAttribute and since it has been pruned
    // we can also remove this inner class relationship.
    return enclosingMethodAttribute == null || !isPinned(configuration);
  }

  public abstract boolean isTop();

  public abstract boolean isBottom();

  public boolean isLessThanOrEquals(K other) {
    // An item is less, aka, lower in the lattice, if each of its attributes is at least as
    // permissive of that on other.
    return (allowAccessModification || !other.internalIsAccessModificationAllowed())
        && (allowAccessModificationForTesting
            || !other.internalIsAccessModificationAllowedForTesting())
        && (allowAnnotationRemoval || !other.internalIsAnnotationRemovalAllowed())
        && (allowMinification || !other.internalIsMinificationAllowed())
        && (allowOptimization || !other.internalIsOptimizationAllowed())
        && (allowShrinking || !other.internalIsShrinkingAllowed())
        && (allowSignatureRemoval || !other.internalIsSignatureRemovalAllowed())
        && (!checkDiscarded || other.internalIsCheckDiscardedEnabled());
  }

  /** Builder to construct an arbitrary keep info object. */
  public abstract static class Builder<B extends Builder<B, K>, K extends KeepInfo<B, K>> {

    abstract B self();

    abstract K doBuild();

    abstract K getTopInfo();

    abstract K getBottomInfo();

    abstract boolean isEqualTo(K other);

    protected K original;
    private boolean allowAccessModification;
    private boolean allowAccessModificationForTesting;
    private boolean allowAnnotationRemoval;
    private boolean allowMinification;
    private boolean allowOptimization;
    private boolean allowShrinking;
    private boolean allowSignatureRemoval;
    private boolean checkDiscarded;

    Builder() {
      // Default initialized. Use should be followed by makeTop/makeBottom.
    }

    Builder(K original) {
      this.original = original;
      allowAccessModification = original.internalIsAccessModificationAllowed();
      allowAccessModificationForTesting = original.internalIsAccessModificationAllowedForTesting();
      allowAnnotationRemoval = original.internalIsAnnotationRemovalAllowed();
      allowMinification = original.internalIsMinificationAllowed();
      allowOptimization = original.internalIsOptimizationAllowed();
      allowShrinking = original.internalIsShrinkingAllowed();
      allowSignatureRemoval = original.internalIsSignatureRemovalAllowed();
      checkDiscarded = original.internalIsCheckDiscardedEnabled();
    }

    B makeTop() {
      disallowAccessModification();
      disallowAccessModificationForTesting();
      disallowAnnotationRemoval();
      disallowMinification();
      disallowOptimization();
      disallowShrinking();
      disallowSignatureRemoval();
      unsetCheckDiscarded();
      return self();
    }

    B makeBottom() {
      allowAccessModification();
      allowAccessModificationForTesting();
      allowAnnotationRemoval();
      allowMinification();
      allowOptimization();
      allowShrinking();
      allowSignatureRemoval();
      unsetCheckDiscarded();
      return self();
    }

    public K build() {
      if (original != null) {
        if (internalIsEqualTo(original)) {
          return original;
        }
        if (internalIsEqualTo(getTopInfo())) {
          return getTopInfo();
        }
        if (internalIsEqualTo(getBottomInfo())) {
          return getBottomInfo();
        }
      }
      return doBuild();
    }

    boolean internalIsEqualTo(K other) {
      return isAccessModificationAllowed() == other.internalIsAccessModificationAllowed()
          && isAccessModificationAllowedForTesting()
              == other.internalIsAccessModificationAllowedForTesting()
          && isAnnotationRemovalAllowed() == other.internalIsAnnotationRemovalAllowed()
          && isMinificationAllowed() == other.internalIsMinificationAllowed()
          && isOptimizationAllowed() == other.internalIsOptimizationAllowed()
          && isShrinkingAllowed() == other.internalIsShrinkingAllowed()
          && isSignatureRemovalAllowed() == other.internalIsSignatureRemovalAllowed()
          && isCheckDiscardedEnabled() == other.internalIsCheckDiscardedEnabled();
    }

    public boolean isAccessModificationAllowed() {
      return allowAccessModification;
    }

    public boolean isAccessModificationAllowedForTesting() {
      return allowAccessModificationForTesting;
    }

    public boolean isAnnotationRemovalAllowed() {
      return allowAnnotationRemoval;
    }

    public boolean isCheckDiscardedEnabled() {
      return checkDiscarded;
    }

    public boolean isMinificationAllowed() {
      return allowMinification;
    }

    public boolean isOptimizationAllowed() {
      return allowOptimization;
    }

    public boolean isShrinkingAllowed() {
      return allowShrinking;
    }

    public boolean isSignatureRemovalAllowed() {
      return allowSignatureRemoval;
    }

    public B setAllowMinification(boolean allowMinification) {
      this.allowMinification = allowMinification;
      return self();
    }

    public B allowMinification() {
      return setAllowMinification(true);
    }

    public B disallowMinification() {
      return setAllowMinification(false);
    }

    public B setAllowOptimization(boolean allowOptimization) {
      this.allowOptimization = allowOptimization;
      return self();
    }

    public B allowOptimization() {
      return setAllowOptimization(true);
    }

    public B disallowOptimization() {
      return setAllowOptimization(false);
    }

    public B setAllowShrinking(boolean allowShrinking) {
      this.allowShrinking = allowShrinking;
      return self();
    }

    public B allowShrinking() {
      return setAllowShrinking(true);
    }

    public B disallowShrinking() {
      return setAllowShrinking(false);
    }

    public B setCheckDiscarded(boolean checkDiscarded) {
      this.checkDiscarded = checkDiscarded;
      return self();
    }

    public B setCheckDiscarded() {
      return setCheckDiscarded(true);
    }

    public B unsetCheckDiscarded() {
      return setCheckDiscarded(false);
    }

    public B setAllowAccessModification(boolean allowAccessModification) {
      this.allowAccessModification = allowAccessModification;
      return self();
    }

    public B allowAccessModification() {
      return setAllowAccessModification(true);
    }

    public B disallowAccessModification() {
      return setAllowAccessModification(false);
    }

    public B setAllowAccessModificationForTesting(boolean allowAccessModificationForTesting) {
      this.allowAccessModificationForTesting = allowAccessModificationForTesting;
      return self();
    }

    public B allowAccessModificationForTesting() {
      return setAllowAccessModificationForTesting(true);
    }

    public B disallowAccessModificationForTesting() {
      return setAllowAccessModificationForTesting(false);
    }

    public B setAllowAnnotationRemoval(boolean allowAnnotationRemoval) {
      this.allowAnnotationRemoval = allowAnnotationRemoval;
      return self();
    }

    public B allowAnnotationRemoval() {
      return setAllowAnnotationRemoval(true);
    }

    public B disallowAnnotationRemoval() {
      return setAllowAnnotationRemoval(false);
    }

    private B setAllowSignatureRemoval(boolean allowSignatureRemoval) {
      this.allowSignatureRemoval = allowSignatureRemoval;
      return self();
    }

    public B allowSignatureRemoval() {
      return setAllowSignatureRemoval(true);
    }

    public B disallowSignatureRemoval() {
      return setAllowSignatureRemoval(false);
    }
  }

  /** Joiner to construct monotonically increasing keep info object. */
  public abstract static class Joiner<
      J extends Joiner<J, B, K>, B extends Builder<B, K>, K extends KeepInfo<B, K>> {

    abstract J self();

    final B builder;

    /**
     * The set of reasons and rules that have contributed to setting {@link Builder#allowShrinking}
     * to false on this joiner. These are needed to report the correct -whyareyoukeeping reasons for
     * rooted items.
     *
     * <p>An item should only have allowShrinking set to false if it is kept by a -keep rule or the
     * {@link Enqueuer} detects a reflective access to the item (hence the {@link
     * Set<ReflectiveUseFrom>}).
     *
     * <p>These are only needed for the interpretation of keep rules into keep info, and is
     * therefore not stored in the keep info builder above.
     */
    final Set<ReflectiveUseFrom> reasons = new HashSet<>();

    final Set<ProguardKeepRuleBase> rules = Sets.newIdentityHashSet();

    Joiner(B builder) {
      this.builder = builder;
    }

    public J applyIf(boolean condition, Consumer<J> thenConsumer) {
      if (condition) {
        thenConsumer.accept(self());
      }
      return self();
    }

    public KeepClassInfo.Joiner asClassJoiner() {
      return null;
    }

    public KeepFieldInfo.Joiner asFieldJoiner() {
      return null;
    }

    public static KeepFieldInfo.Joiner asFieldJoinerOrNull(Joiner<?, ?, ?> joiner) {
      return joiner != null ? joiner.asFieldJoiner() : null;
    }

    public KeepMethodInfo.Joiner asMethodJoiner() {
      return null;
    }

    public Set<ReflectiveUseFrom> getReasons() {
      return reasons;
    }

    public Set<ProguardKeepRuleBase> getRules() {
      return rules;
    }

    public boolean isBottom() {
      return builder.isEqualTo(builder.getBottomInfo());
    }

    public boolean isCheckDiscardedEnabled() {
      return builder.isCheckDiscardedEnabled();
    }

    public boolean isOptimizationAllowed() {
      return builder.isOptimizationAllowed();
    }

    public boolean isShrinkingAllowed() {
      return builder.isShrinkingAllowed();
    }

    public boolean isTop() {
      return builder.isEqualTo(builder.getTopInfo());
    }

    public J top() {
      builder.makeTop();
      return self();
    }

    public J addReason(ReflectiveUseFrom reason) {
      reasons.add(reason);
      return self();
    }

    public J addRule(ProguardKeepRuleBase rule) {
      rules.add(rule);
      return self();
    }

    public J disallowAccessModification() {
      builder.disallowAccessModification();
      return self();
    }

    public J disallowAccessModificationForTesting() {
      builder.disallowAccessModificationForTesting();
      return self();
    }

    public J disallowAnnotationRemoval() {
      builder.disallowAnnotationRemoval();
      return self();
    }

    public J disallowMinification() {
      builder.disallowMinification();
      return self();
    }

    public J disallowOptimization() {
      builder.disallowOptimization();
      return self();
    }

    public J disallowShrinking() {
      builder.disallowShrinking();
      return self();
    }

    public J disallowSignatureRemoval() {
      builder.disallowSignatureRemoval();
      return self();
    }

    public J setCheckDiscarded() {
      builder.setCheckDiscarded();
      return self();
    }

    public J merge(J joiner) {
      Builder<B, K> builder = joiner.builder;
      applyIf(!builder.isAccessModificationAllowed(), Joiner::disallowAccessModification);
      applyIf(
          !builder.isAccessModificationAllowedForTesting(),
          Joiner::disallowAccessModificationForTesting);
      applyIf(!builder.isAnnotationRemovalAllowed(), Joiner::disallowAnnotationRemoval);
      applyIf(!builder.isMinificationAllowed(), Joiner::disallowMinification);
      applyIf(!builder.isOptimizationAllowed(), Joiner::disallowOptimization);
      applyIf(!builder.isShrinkingAllowed(), Joiner::disallowShrinking);
      applyIf(!builder.isSignatureRemovalAllowed(), Joiner::disallowSignatureRemoval);
      applyIf(builder.isCheckDiscardedEnabled(), Joiner::setCheckDiscarded);
      reasons.addAll(joiner.reasons);
      rules.addAll(joiner.rules);
      return self();
    }

    @SuppressWarnings("unchecked")
    public J mergeUnsafe(Joiner<?, ?, ?> joiner) {
      return merge((J) joiner);
    }

    public K join() {
      K joined = builder.build();
      K original = builder.original;
      assert original.isLessThanOrEquals(joined);
      return joined;
    }

    public boolean verifyShrinkingDisallowedWithRule(InternalOptions options) {
      assert !isShrinkingAllowed();
      assert !getReasons().isEmpty() || !getRules().isEmpty() || !options.isShrinking();
      return true;
    }
  }
}
