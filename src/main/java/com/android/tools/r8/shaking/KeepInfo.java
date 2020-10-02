// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.shaking.KeepInfo.Builder;

/** Keep information that can be associated with any item, i.e., class, method or field. */
public abstract class KeepInfo<B extends Builder<B, K>, K extends KeepInfo<B, K>> {

  private final boolean pinned;
  private final boolean allowMinification;
  private final boolean allowAccessModification;
  private final boolean requireAccessModificationForRepackaging;

  private KeepInfo(
      boolean pinned,
      boolean allowMinification,
      boolean allowAccessModification,
      boolean requireAccessModificationForRepackaging) {
    this.pinned = pinned;
    this.allowMinification = allowMinification;
    this.allowAccessModification = allowAccessModification;
    this.requireAccessModificationForRepackaging = requireAccessModificationForRepackaging;
  }

  KeepInfo(B builder) {
    this(
        builder.isPinned(),
        builder.isMinificationAllowed(),
        builder.isAccessModificationAllowed(),
        builder.isAccessModificationRequiredForRepackaging());
  }

  abstract B builder();

  /** True if an item must be present in the output. */
  public boolean isPinned() {
    return pinned;
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
   * True if an item may be repackaged.
   *
   * <p>This method requires knowledge of the global configuration as that can override the concrete
   * value on a given item.
   */
  public abstract boolean isRepackagingAllowed(GlobalKeepInfoConfiguration configuration);

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

  public abstract boolean isTop();

  public abstract boolean isBottom();

  public boolean isLessThanOrEquals(K other) {
    // An item is less, aka, lower in the lattice, if each of its attributes is at least as
    // permissive of that on other.
    return (!pinned || other.isPinned())
        && (allowAccessModification || !other.internalIsAccessModificationAllowed());
  }

  /** Builder to construct an arbitrary keep info object. */
  public abstract static class Builder<B extends Builder<B, K>, K extends KeepInfo<B, K>> {

    abstract B self();

    abstract K doBuild();

    abstract K getTopInfo();

    abstract K getBottomInfo();

    abstract boolean isEqualTo(K other);

    private K original;
    private boolean pinned;
    private boolean allowMinification;
    private boolean allowAccessModification;
    private boolean requireAccessModificationForRepackaging;

    Builder() {
      // Default initialized. Use should be followed by makeTop/makeBottom.
    }

    Builder(K original) {
      this.original = original;
      pinned = original.isPinned();
      allowMinification = original.internalIsMinificationAllowed();
      allowAccessModification = original.internalIsAccessModificationAllowed();
      requireAccessModificationForRepackaging =
          original.internalIsAccessModificationRequiredForRepackaging();
    }

    B makeTop() {
      pin();
      disallowMinification();
      requireAccessModificationForRepackaging();
      disallowAccessModification();
      return self();
    }

    B makeBottom() {
      unpin();
      allowMinification();
      unsetRequireAccessModificationForRepackaging();
      allowAccessModification();
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

    private boolean internalIsEqualTo(K other) {
      return isPinned() == other.isPinned()
          && isMinificationAllowed() == other.internalIsMinificationAllowed()
          && isAccessModificationRequiredForRepackaging()
              == other.internalIsAccessModificationRequiredForRepackaging()
          && isAccessModificationAllowed() == other.internalIsAccessModificationAllowed()
          && isEqualTo(other);
    }

    public boolean isPinned() {
      return pinned;
    }

    public boolean isMinificationAllowed() {
      return allowMinification;
    }

    public boolean isAccessModificationRequiredForRepackaging() {
      return requireAccessModificationForRepackaging;
    }

    public boolean isAccessModificationAllowed() {
      return allowAccessModification;
    }

    public B setPinned(boolean pinned) {
      this.pinned = pinned;
      return self();
    }

    public B pin() {
      return setPinned(true);
    }

    public B unpin() {
      return setPinned(false);
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
  }

  /** Joiner to construct monotonically increasing keep info object. */
  public abstract static class Joiner<
      J extends Joiner<J, B, K>, B extends Builder<B, K>, K extends KeepInfo<B, K>> {

    abstract J self();

    private final Builder<B, K> builder;

    Joiner(Builder<B, K> builder) {
      this.builder = builder;
    }

    public boolean isTop() {
      return builder.isEqualTo(builder.getTopInfo());
    }

    public J top() {
      builder.makeTop();
      return self();
    }

    public J pin() {
      builder.pin();
      return self();
    }

    public J disallowMinification() {
      builder.disallowMinification();
      return self();
    }

    public J disallowAccessModification() {
      builder.disallowAccessModification();
      return self();
    }

    public J requireAccessModificationForRepackaging() {
      builder.requireAccessModificationForRepackaging();
      return self();
    }

    public K join() {
      K joined = builder.build();
      K original = builder.original;
      assert original.isLessThanOrEquals(joined);
      return joined;
    }
  }
}
