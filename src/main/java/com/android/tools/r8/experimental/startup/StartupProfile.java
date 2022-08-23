// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.startup.StartupProfileProvider;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class StartupProfile {

  private final List<StartupItem<DexType, DexMethod, ?>> startupItems;

  public StartupProfile(List<StartupItem<DexType, DexMethod, ?>> startupItems) {
    this.startupItems = startupItems;
  }

  public static Builder builder() {
    return new Builder();
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
    if (!options.getStartupOptions().hasStartupProfileProvider()) {
      return null;
    }
    StartupProfileProvider resource = options.getStartupOptions().getStartupProfileProvider();
    List<String> startupDescriptors = StringUtils.splitLines(resource.get());
    return createStartupConfigurationFromLines(options, startupDescriptors);
  }

  public static StartupProfile createStartupConfigurationFromLines(
      InternalOptions options, List<String> startupDescriptors) {
    List<StartupItem<DexType, DexMethod, ?>> startupItems = new ArrayList<>();
    StartupConfigurationParser.createDexParser(options.dexItemFactory())
        .parseLines(
            startupDescriptors,
            startupItems::add,
            startupItems::add,
            error ->
                options.reporter.warning(
                    new StringDiagnostic(
                        "Invalid descriptor for startup class or method: " + error)));
    return new StartupProfile(startupItems);
  }

  public List<StartupItem<DexType, DexMethod, ?>> getStartupItems() {
    return startupItems;
  }

  public String serializeToString() {
    StringBuilder builder = new StringBuilder();
    for (StartupItem<DexType, DexMethod, ?> startupItem : startupItems) {
      startupItem.serializeToString(builder, DexType::toSmaliString, DexMethod::toSmaliString);
      builder.append('\n');
    }
    return builder.toString();
  }

  public static class Builder {

    private final ImmutableList.Builder<StartupItem<DexType, DexMethod, ?>> startupItemsBuilder =
        ImmutableList.builder();

    public Builder addStartupItem(StartupItem<DexType, DexMethod, ?> startupItem) {
      this.startupItemsBuilder.add(startupItem);
      return this;
    }

    public Builder addStartupClass(StartupClass<DexType, DexMethod> startupClass) {
      return addStartupItem(startupClass);
    }

    public Builder addStartupMethod(StartupMethod<DexType, DexMethod> startupMethod) {
      return addStartupItem(startupMethod);
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
