// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup;

import com.android.tools.r8.experimental.startup.profile.StartupItem;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ThrowNullCode;
import com.android.tools.r8.profile.art.ArtProfileBuilderUtils.SyntheticToSyntheticContextGeneralization;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMaps;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StartupCompleteness {

  private final AppView<?> appView;
  private final StartupOrder startupOrder;

  private StartupCompleteness(AppView<?> appView) {
    SyntheticToSyntheticContextGeneralization syntheticToSyntheticContextGeneralization =
        appView.enableWholeProgramOptimizations()
            ? SyntheticToSyntheticContextGeneralization.createForR8()
            : SyntheticToSyntheticContextGeneralization.createForD8();
    this.appView = appView;
    this.startupOrder =
        appView.hasClassHierarchy()
            ? appView.appInfoWithClassHierarchy().getStartupOrder()
            : StartupOrder.createInitialStartupOrder(
                appView.options(), null, syntheticToSyntheticContextGeneralization);
  }

  /**
   * Replaces the code of each non-startup method by {@code throw null}. If the application fails on
   * launch with this enabled this points to the startup configuration being incomplete, or
   * inadequate lens rewriting of the startup list in R8.
   */
  public static void run(AppView<?> appView) {
    InternalOptions options = appView.options();
    if (options.getStartupOptions().isStartupCompletenessCheckForTestingEnabled()) {
      new StartupCompleteness(appView).processClasses();
    }
  }

  private void processClasses() {
    Set<DexReference> startupItems = computeStartupItems();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      processClass(clazz, startupItems);
    }
  }

  private void processClass(DexProgramClass clazz, Set<DexReference> startupItems) {
    clazz.forEachProgramMethodMatching(
        method -> !startupItems.contains(method.getReference()), this::processNonStartupMethod);
    if (!startupItems.contains(clazz.getType())) {
      if (clazz.hasClassInitializer()) {
        processNonStartupMethod(clazz.getProgramClassInitializer());
      } else {
        clazz.addDirectMethod(
            DexEncodedMethod.syntheticBuilder()
                .setAccessFlags(MethodAccessFlags.createForClassInitializer())
                .setCode(ThrowNullCode.get())
                .setMethod(appView.dexItemFactory().createClassInitializer(clazz.getType()))
                .build());
      }
    }
  }

  private void processNonStartupMethod(ProgramMethod method) {
    method.getDefinition().setCode(ThrowNullCode.get(), Int2ReferenceMaps.emptyMap());
  }

  private Set<DexReference> computeStartupItems() {
    Set<DexReference> startupItems = Sets.newIdentityHashSet();
    Map<DexType, List<DexProgramClass>> syntheticContextsToSyntheticClasses =
        appView.getSyntheticItems().computeSyntheticContextsToSyntheticClasses(appView);
    for (StartupItem startupItem : startupOrder.getItems()) {
      startupItem.accept(
          startupClass -> startupItems.add(startupClass.getReference()),
          startupMethod -> startupItems.add(startupMethod.getReference()),
          syntheticStartupMethod -> {
            List<DexProgramClass> syntheticClasses =
                syntheticContextsToSyntheticClasses.getOrDefault(
                    syntheticStartupMethod.getSyntheticContextType(), Collections.emptyList());
            for (DexProgramClass syntheticClass : syntheticClasses) {
              startupItems.add(syntheticClass.getType());
              syntheticClass.forEachProgramMethod(
                  method -> startupItems.add(method.getReference()));
            }
          });
    }
    return startupItems;
  }
}
