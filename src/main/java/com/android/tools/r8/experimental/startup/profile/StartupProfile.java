// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup.profile;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.startup.StartupClassBuilder;
import com.android.tools.r8.startup.StartupMethodBuilder;
import com.android.tools.r8.startup.StartupProfileBuilder;
import com.android.tools.r8.startup.StartupProfileProvider;
import com.android.tools.r8.startup.SyntheticStartupMethodBuilder;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public class StartupProfile {

  private final List<StartupItem> startupItems;

  public StartupProfile(List<StartupItem> startupItems) {
    this.startupItems = startupItems;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(DexItemFactory dexItemFactory) {
    return new Builder(dexItemFactory);
  }

  /**
   * Parses the supplied startup configuration, if any. The startup configuration is a list of class
   * and method descriptors.
   *
   * <p>Example:
   *
   * <pre>
   * Landroidx/compose/runtime/ComposerImpl;->updateValue(Ljava/lang/Object;)V
   * Landroidx/compose/runtime/ComposerImpl;->updatedNodeCount(I)I
   * Landroidx/compose/runtime/ComposerImpl;->validateNodeExpected()V
   * Landroidx/compose/runtime/CompositionImpl;->applyChanges()V
   * Landroidx/compose/runtime/ComposerKt;->findLocation(Ljava/util/List;I)I
   * Landroidx/compose/runtime/ComposerImpl;
   * </pre>
   */
  public static StartupProfile parseStartupProfile(InternalOptions options) {
    if (!options.getStartupOptions().hasStartupProfileProviders()) {
      return null;
    }
    Collection<StartupProfileProvider> startupProfileProviders =
        options.getStartupOptions().getStartupProfileProviders();
    StartupProfile.Builder startupProfileBuilder = StartupProfile.builder(options.dexItemFactory());
    for (StartupProfileProvider startupProfileProvider : startupProfileProviders) {
      startupProfileProvider.getStartupProfile(startupProfileBuilder);
    }
    return startupProfileBuilder.build();
  }

  public List<StartupItem> getStartupItems() {
    return startupItems;
  }

  public static class Builder implements StartupProfileBuilder {

    private final DexItemFactory dexItemFactory;
    private final ImmutableList.Builder<StartupItem> startupItemsBuilder = ImmutableList.builder();

    Builder() {
      this(null);
    }

    Builder(DexItemFactory dexItemFactory) {
      this.dexItemFactory = dexItemFactory;
    }

    @Override
    public Builder addStartupClass(Consumer<StartupClassBuilder> startupClassBuilderConsumer) {
      StartupClass.Builder startupClassBuilder = StartupClass.builder(dexItemFactory);
      startupClassBuilderConsumer.accept(startupClassBuilder);
      return addStartupItem(startupClassBuilder.build());
    }

    @Override
    public Builder addStartupMethod(Consumer<StartupMethodBuilder> startupMethodBuilderConsumer) {
      StartupMethod.Builder startupMethodBuilder = StartupMethod.builder(dexItemFactory);
      startupMethodBuilderConsumer.accept(startupMethodBuilder);
      return addStartupItem(startupMethodBuilder.build());
    }

    @Override
    public StartupProfileBuilder addSyntheticStartupMethod(
        Consumer<SyntheticStartupMethodBuilder> syntheticStartupMethodBuilderConsumer) {
      SyntheticStartupMethod.Builder syntheticStartupMethodBuilder =
          SyntheticStartupMethod.builder(dexItemFactory);
      syntheticStartupMethodBuilderConsumer.accept(syntheticStartupMethodBuilder);
      return addStartupItem(syntheticStartupMethodBuilder.build());
    }

    private Builder addStartupItem(StartupItem startupItem) {
      this.startupItemsBuilder.add(startupItem);
      return this;
    }

    public Builder apply(Consumer<Builder> consumer) {
      consumer.accept(this);
      return this;
    }

    public StartupProfile build() {
      return new StartupProfile(startupItemsBuilder.build());
    }
  }
}
