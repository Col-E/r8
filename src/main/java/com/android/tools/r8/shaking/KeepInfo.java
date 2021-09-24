// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.EnclosingMethodAttribute;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.shaking.KeepInfo.Builder;
import com.google.common.collect.Sets;
import java.util.Set;
import java.util.function.Consumer;

/** Keep information that can be associated with any item, i.e., class, method or field. */
public abstract class KeepInfo<B extends Builder<B, K>, K extends KeepInfo<B, K>> {

  private final boolean allowAccessModification;
  private final boolean allowAnnotationRemoval;
  private final boolean allowMinification;
  private final boolean allowOptimization;
  private final boolean allowShrinking;
  private final boolean checkDiscarded;
  private final boolean requireAccessModificationForRepackaging;

  private KeepInfo(
      boolean allowAccessModification,
      boolean allowAnnotationRemoval,
      boolean allowMinification,
      boolean allowOptimization,
      boolean allowShrinking,
      boolean checkDiscarded,
      boolean requireAccessModificationForRepackaging) {
    this.allowAccessModification = allowAccessModification;
    this.allowAnnotationRemoval = allowAnnotationRemoval;
    this.allowMinification = allowMinification;
    this.allowOptimization = allowOptimization;
    this.allowShrinking = allowShrinking;
    this.checkDiscarded = checkDiscarded;
    this.requireAccessModificationForRepackaging = requireAccessModificationForRepackaging;
  }

  KeepInfo(B builder) {
    this(
        builder.isAccessModificationAllowed(),
        builder.isAnnotationRemovalAllowed(),
        builder.isMinificationAllowed(),
        builder.isOptimizationAllowed(),
        builder.isShrinkingAllowed(),
        builder.isCheckDiscardedEnabled(),
        builder.isAccessModificationRequiredForRepackaging());
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

  public boolean isParameterRemovalAllowed(GlobalKeepInfoConfiguration configuration) {
    return isOptimizationAllowed(configuration)
        && isShrinkingAllowed(configuration)
        && !isCheckDiscardedEnabled(configuration);
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
   * True if an item may be repackaged.
   *
   * <p>This method requires knowledge of the global configuration as that can override the concrete
   * value on a given item.
   */
  public abstract boolean isRepackagingAllowed(
      ProgramDefinition definition, GlobalKeepInfoConfiguration configuration);

  boolean internalIsAccessModificationRequiredForRepackaging() {
    return requireAccessModificationForRepackaging;
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

  public boolean isSignatureAttributeRemovalAllowed(GlobalKeepInfoConfiguration configuration) {
    if (!configuration.isKeepAttributesSignatureEnabled()) {
      return true;
    }
    return !(configuration.isForceProguardCompatibilityEnabled() || isPinned(configuration));
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
        && (allowAnnotationRemoval || !other.internalIsAnnotationRemovalAllowed())
        && (allowMinification || !other.internalIsMinificationAllowed())
        && (allowOptimization || !other.internalIsOptimizationAllowed())
        && (allowShrinking || !other.internalIsShrinkingAllowed())
        && (!checkDiscarded || other.internalIsCheckDiscardedEnabled());
  }

  /** Builder to construct an arbitrary keep info object. */
  public abstract static class Builder<B extends Builder<B, K>, K extends KeepInfo<B, K>> {

    abstract B self();

    abstract K doBuild();

    abstract K getTopInfo();

    abstract K getBottomInfo();

    abstract boolean isEqualTo(K other);

    private K original;
    private boolean allowAccessModification;
    private boolean allowAnnotationRemoval;
    private boolean allowMinification;
    private boolean allowOptimization;
    private boolean allowShrinking;
    private boolean checkDiscarded;
    private boolean requireAccessModificationForRepackaging;

    Builder() {
      // Default initialized. Use should be followed by makeTop/makeBottom.
    }

    Builder(K original) {
      this.original = original;
      allowAccessModification = original.internalIsAccessModificationAllowed();
      allowAnnotationRemoval = original.internalIsAnnotationRemovalAllowed();
      allowMinification = original.internalIsMinificationAllowed();
      allowOptimization = original.internalIsOptimizationAllowed();
      allowShrinking = original.internalIsShrinkingAllowed();
      checkDiscarded = original.internalIsCheckDiscardedEnabled();
      requireAccessModificationForRepackaging =
          original.internalIsAccessModificationRequiredForRepackaging();
    }

    B makeTop() {
      disallowAccessModification();
      disallowAnnotationRemoval();
      disallowMinification();
      disallowOptimization();
      disallowShrinking();
      unsetCheckDiscarded();
      requireAccessModificationForRepackaging();
      return self();
    }

    B makeBottom() {
      allowAccessModification();
      allowAnnotationRemoval();
      allowMinification();
      allowOptimization();
      allowShrinking();
      unsetCheckDiscarded();
      unsetRequireAccessModificationForRepackaging();
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
          && isAnnotationRemovalAllowed() == other.internalIsAnnotationRemovalAllowed()
          && isMinificationAllowed() == other.internalIsMinificationAllowed()
          && isOptimizationAllowed() == other.internalIsOptimizationAllowed()
          && isShrinkingAllowed() == other.internalIsShrinkingAllowed()
          && isCheckDiscardedEnabled() == other.internalIsCheckDiscardedEnabled()
          && isAccessModificationRequiredForRepackaging()
              == other.internalIsAccessModificationRequiredForRepackaging();
    }

    public boolean isAccessModificationRequiredForRepackaging() {
      return requireAccessModificationForRepackaging;
    }

    public boolean isAccessModificationAllowed() {
      return allowAccessModification;
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

    public B setRequireAccessModificationForRepackaging(
        boolean requireAccessModificationForRepackaging) {
      this.requireAccessModificationForRepackaging = requireAccessModificationForRepackaging;
      return self();
    }

    public B requireAccessModificationForRepackaging() {
      return setRequireAccessModificationForRepackaging(true);
    }

    public B unsetRequireAccessModificationForRepackaging() {
      return setRequireAccessModificationForRepackaging(false);
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
  }

  /** Joiner to construct monotonically increasing keep info object. */
  public abstract static class Joiner<
      J extends Joiner<J, B, K>, B extends Builder<B, K>, K extends KeepInfo<B, K>> {

    abstract J self();

    final Builder<B, K> builder;

    // The set of rules that have contributed to this joiner. These are only needed for the
    // interpretation of keep rules into keep info, and is therefore not stored in the keep info
    // builder above.
    final Set<ProguardKeepRuleBase> rules = Sets.newIdentityHashSet();

    Joiner(Builder<B, K> builder) {
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

    public KeepMethodInfo.Joiner asMethodJoiner() {
      return null;
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

    public J addRule(ProguardKeepRuleBase rule) {
      rules.add(rule);
      return self();
    }

    public J disallowAccessModification() {
      builder.disallowAccessModification();
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

    public J setCheckDiscarded() {
      builder.setCheckDiscarded();
      return self();
    }

    public J requireAccessModificationForRepackaging() {
      builder.requireAccessModificationForRepackaging();
      return self();
    }

    public J merge(J joiner) {
      Builder<B, K> builder = joiner.builder;
      applyIf(!builder.isAccessModificationAllowed(), Joiner::disallowAccessModification);
      applyIf(!builder.isAnnotationRemovalAllowed(), Joiner::disallowAnnotationRemoval);
      applyIf(!builder.isMinificationAllowed(), Joiner::disallowMinification);
      applyIf(!builder.isOptimizationAllowed(), Joiner::disallowOptimization);
      applyIf(!builder.isShrinkingAllowed(), Joiner::disallowShrinking);
      applyIf(builder.isCheckDiscardedEnabled(), Joiner::setCheckDiscarded);
      applyIf(
          builder.isAccessModificationRequiredForRepackaging(),
          Joiner::requireAccessModificationForRepackaging);
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
  }
}
