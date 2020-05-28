// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.shaking.KeepInfo.Builder;
import java.util.function.Supplier;

/** Keep information that can be associated with any item, i.e., class, method or field. */
public abstract class KeepInfo<B extends Builder, K extends KeepInfo> {

  private final boolean pinned;
  private final boolean allowAccessModification;

  private KeepInfo(boolean pinned, boolean allowAccessModification) {
    this.pinned = pinned;
    this.allowAccessModification = allowAccessModification;
  }

  KeepInfo(B builder) {
    this(builder.isPinned(), builder.isAccessModificationAllowed());
  }

  /** True if an item must be present in the output. */
  public boolean isPinned() {
    return pinned;
  }

  /**
   * True if an item may have its access flags modified.
   *
   * <p>This method requires knowledge of the global access modification as that will override the
   * concrete value on a given item.
   *
   * @param configuration Global configuration object to determine access modification.
   */
  public boolean isAccessModificationAllowed(ProguardConfiguration configuration) {
    return configuration.isAccessModificationAllowed() && internalIsAccessModificationAllowed();
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
  public abstract static class Builder<B extends Builder, K extends KeepInfo> {

    abstract B self();

    abstract K doBuild();

    abstract K getTopInfo();

    abstract K getBottomInfo();

    abstract boolean isEqualTo(K other);

    private K original;
    private boolean pinned;
    private boolean allowAccessModification;

    Builder() {
      // Default initialized. Use should be followed by makeTop/makeBottom.
    }

    Builder(K original) {
      this.original = original;
      pinned = original.isPinned();
      allowAccessModification = original.internalIsAccessModificationAllowed();
    }

    B makeTop() {
      pin();
      disallowAccessModification();
      return self();
    }

    B makeBottom() {
      unpin();
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
          && isAccessModificationAllowed() == other.internalIsAccessModificationAllowed()
          && isEqualTo(other);
    }

    public boolean isPinned() {
      return pinned;
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
      J extends Joiner, B extends Builder, K extends KeepInfo<B, K>> {

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

    // Lazy modification of access modification.
    // Only forced if access modification is still allowed.
    public J lazyDisallowAccessModification(Supplier<Boolean> lazyShouldDisallow) {
      if (builder.isAccessModificationAllowed() && lazyShouldDisallow.get()) {
        builder.disallowAccessModification();
      }
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
