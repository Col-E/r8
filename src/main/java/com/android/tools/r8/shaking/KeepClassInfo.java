// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.InternalOptions;
import java.util.function.Function;

/** Immutable keep requirements for a class. */
public final class KeepClassInfo extends KeepInfo<KeepClassInfo.Builder, KeepClassInfo> {

  // Requires all aspects of a class to be kept.
  private static final KeepClassInfo TOP =
      new Builder().makeTop().disallowRepackaging().disallowPermittedSubclassesRemoval().build();

  // Requires no aspects of a class to be kept.
  private static final KeepClassInfo BOTTOM =
      new Builder().makeBottom().allowRepackaging().allowPermittedSubclassesRemoval().build();

  public static KeepClassInfo top() {
    return TOP;
  }

  public static KeepClassInfo bottom() {
    return BOTTOM;
  }

  public static Joiner newEmptyJoiner() {
    return bottom().joiner();
  }

  private final boolean allowRepackaging;
  private final boolean checkEnumUnboxed;
  private final boolean allowPermittedSubclassesRemoval;

  private KeepClassInfo(Builder builder) {
    super(builder);
    this.allowRepackaging = builder.isRepackagingAllowed();
    this.checkEnumUnboxed = builder.isCheckEnumUnboxedEnabled();
    this.allowPermittedSubclassesRemoval = builder.isPermittedSubclassesRemovalAllowed();
  }

  @Override
  Builder builder() {
    return new Builder(this);
  }

  public boolean isCheckEnumUnboxedEnabled(GlobalKeepInfoConfiguration configuration) {
    return internalIsCheckEnumUnboxedEnabled();
  }

  boolean internalIsCheckEnumUnboxedEnabled() {
    return checkEnumUnboxed;
  }

  public Joiner joiner() {
    assert !isTop();
    return new Joiner(this);
  }

  public boolean isPermittedSubclassesRemovalAllowed(GlobalKeepInfoConfiguration configuration) {
    return !configuration.isKeepPermittedSubclassesEnabled()
        && internalIsPermittedSubclassesRemovalAllowed();
  }

  /**
   * True if an item may be repackaged.
   *
   * <p>This method requires knowledge of the global configuration as that can override the concrete
   * value on a given item.
   */
  public boolean isRepackagingAllowed(GlobalKeepInfoConfiguration configuration) {
    return configuration.isRepackagingEnabled() && internalIsRepackagingAllowed();
  }

  boolean internalIsRepackagingAllowed() {
    return allowRepackaging;
  }

  public boolean isKotlinMetadataRemovalAllowed(
      GlobalKeepInfoConfiguration configuration, boolean kotlinMetadataKept) {
    return !kotlinMetadataKept
        || !isPinned(configuration)
        || !configuration.isKeepRuntimeVisibleAnnotationsEnabled()
        || isAnnotationRemovalAllowed(configuration);
  }

  public static boolean isKotlinMetadataClassKept(
      DexItemFactory factory,
      InternalOptions options,
      Function<DexType, DexClass> definitionForWithoutExistenceAssert,
      Function<DexProgramClass, KeepClassInfo> getClassInfo) {
    DexType kotlinMetadataType = factory.kotlinMetadataType;
    DexClass kotlinMetadataClass = definitionForWithoutExistenceAssert.apply(kotlinMetadataType);
    return kotlinMetadataClass == null
        || kotlinMetadataClass.isNotProgramClass()
        || !getClassInfo.apply(kotlinMetadataClass.asProgramClass()).isShrinkingAllowed(options);
  }

  boolean internalIsPermittedSubclassesRemovalAllowed() {
    return allowPermittedSubclassesRemoval;
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

    private boolean allowRepackaging;
    private boolean checkEnumUnboxed;
    private boolean allowPermittedSubclassesRemoval;

    private Builder() {
      super();
    }

    private Builder(KeepClassInfo original) {
      super(original);
      allowRepackaging = original.internalIsRepackagingAllowed();
      checkEnumUnboxed = original.internalIsCheckEnumUnboxedEnabled();
      allowPermittedSubclassesRemoval = original.internalIsPermittedSubclassesRemovalAllowed();
    }

    // Check enum unboxed.

    public boolean isCheckEnumUnboxedEnabled() {
      return checkEnumUnboxed;
    }

    public Builder setCheckEnumUnboxed(boolean checkEnumUnboxed) {
      this.checkEnumUnboxed = checkEnumUnboxed;
      return self();
    }

    public boolean isRepackagingAllowed() {
      return allowRepackaging;
    }

    private Builder setAllowRepackaging(boolean allowRepackaging) {
      this.allowRepackaging = allowRepackaging;
      return self();
    }

    public Builder allowRepackaging() {
      return setAllowRepackaging(true);
    }

    public Builder disallowRepackaging() {
      return setAllowRepackaging(false);
    }

    public Builder setCheckEnumUnboxed() {
      return setCheckEnumUnboxed(true);
    }

    public Builder unsetCheckEnumUnboxed() {
      return setCheckEnumUnboxed(false);
    }

    public Builder allowPermittedSubclassesRemoval() {
      return setAllowPermittedSubclassesRemoval(true);
    }

    public boolean isPermittedSubclassesRemovalAllowed() {
      return allowPermittedSubclassesRemoval;
    }

    private Builder setAllowPermittedSubclassesRemoval(boolean allowPermittedSubclassesRemoval) {
      this.allowPermittedSubclassesRemoval = allowPermittedSubclassesRemoval;
      return self();
    }

    public Builder disallowPermittedSubclassesRemoval() {
      return setAllowPermittedSubclassesRemoval(false);
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
      return internalIsEqualTo(other);
    }

    @Override
    boolean internalIsEqualTo(KeepClassInfo other) {
      return super.internalIsEqualTo(other)
          && isRepackagingAllowed() == other.internalIsRepackagingAllowed()
          && isCheckEnumUnboxedEnabled() == other.internalIsCheckEnumUnboxedEnabled()
          && isPermittedSubclassesRemovalAllowed()
              == other.internalIsPermittedSubclassesRemovalAllowed();
    }

    @Override
    public KeepClassInfo doBuild() {
      return new KeepClassInfo(this);
    }

    @Override
    public Builder makeTop() {
      return super.makeTop()
          .unsetCheckEnumUnboxed()
          .disallowRepackaging()
          .disallowPermittedSubclassesRemoval();
    }

    @Override
    public Builder makeBottom() {
      return super.makeBottom()
          .unsetCheckEnumUnboxed()
          .allowRepackaging()
          .allowPermittedSubclassesRemoval();
    }
  }

  public static class Joiner extends KeepInfo.Joiner<Joiner, Builder, KeepClassInfo> {

    public Joiner(KeepClassInfo info) {
      super(info.builder());
    }

    public Joiner setCheckEnumUnboxed() {
      builder.setCheckEnumUnboxed();
      return self();
    }

    public Joiner disallowRepackaging() {
      builder.disallowRepackaging();
      return self();
    }

    public Joiner disallowPermittedSubclassesRemoval() {
      builder.disallowPermittedSubclassesRemoval();
      return self();
    }

    public Joiner allowPermittedSubclassesRemoval() {
      builder.allowPermittedSubclassesRemoval();
      return self();
    }

    @Override
    public Joiner asClassJoiner() {
      return this;
    }

    @Override
    public Joiner merge(Joiner joiner) {
      // Should be extended to merge the fields of this class in case any are added.
      return super.merge(joiner)
          .applyIf(joiner.builder.isCheckEnumUnboxedEnabled(), Joiner::setCheckEnumUnboxed)
          .applyIf(!joiner.builder.isRepackagingAllowed(), Joiner::disallowRepackaging)
          .applyIf(
              !joiner.builder.isPermittedSubclassesRemovalAllowed(),
              Joiner::disallowPermittedSubclassesRemoval);
    }

    @Override
    Joiner self() {
      return this;
    }
  }
}
