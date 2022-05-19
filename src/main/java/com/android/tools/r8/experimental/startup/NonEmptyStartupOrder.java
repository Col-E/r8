// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.PrunedItems;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class NonEmptyStartupOrder extends StartupOrder {

  private final LinkedHashSet<DexType> startupClasses;

  NonEmptyStartupOrder(LinkedHashSet<DexType> startupClasses) {
    assert !startupClasses.isEmpty();
    this.startupClasses = startupClasses;
  }

  @Override
  public boolean contains(DexType type) {
    return startupClasses.contains(type);
  }

  @Override
  public Collection<DexType> getClasses() {
    return startupClasses;
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public StartupOrder rewrittenWithLens(GraphLens graphLens) {
    LinkedHashSet<DexType> rewrittenStartupClasses = new LinkedHashSet<>(startupClasses.size());
    for (DexType startupClass : startupClasses) {
      DexType rewrittenStartupClass = graphLens.lookupType(startupClass);
      rewrittenStartupClasses.add(rewrittenStartupClass);
    }
    return createNonEmpty(rewrittenStartupClasses);
  }

  @Override
  public StartupOrder toStartupOrderForWriting(AppView<?> appView) {
    LinkedHashSet<DexType> rewrittenStartupClasses = new LinkedHashSet<>(startupClasses.size());
    Map<DexType, List<DexProgramClass>> syntheticContextsToSyntheticClasses =
        new IdentityHashMap<>();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (appView.getSyntheticItems().isSyntheticClass(clazz)) {
        for (DexType synthesizingContextType :
            appView.getSyntheticItems().getSynthesizingContextTypes(clazz.getType())) {
          syntheticContextsToSyntheticClasses
              .computeIfAbsent(synthesizingContextType, ignoreKey -> new ArrayList<>())
              .add(clazz);
        }
      }
    }
    for (DexType startupClass : startupClasses) {
      addClassAndParentClasses(
          startupClass, rewrittenStartupClasses, syntheticContextsToSyntheticClasses, appView);
    }
    return createNonEmpty(rewrittenStartupClasses);
  }

  private static boolean addClass(
      DexProgramClass clazz, LinkedHashSet<DexType> rewrittenStartupClasses) {
    return rewrittenStartupClasses.add(clazz.getType());
  }

  private static void addClassAndParentClasses(
      DexType type,
      LinkedHashSet<DexType> rewrittenStartupClasses,
      Map<DexType, List<DexProgramClass>> syntheticContextsToSyntheticClasses,
      AppView<?> appView) {
    DexProgramClass definition = appView.app().programDefinitionFor(type);
    if (definition != null) {
      addClassAndParentClasses(
          definition, rewrittenStartupClasses, syntheticContextsToSyntheticClasses, appView);
    }
  }

  private static void addClassAndParentClasses(
      DexProgramClass clazz,
      LinkedHashSet<DexType> rewrittenStartupClasses,
      Map<DexType, List<DexProgramClass>> syntheticContextsToSyntheticClasses,
      AppView<?> appView) {
    if (addClass(clazz, rewrittenStartupClasses)) {
      addSyntheticClassesAndParentClasses(
          clazz, rewrittenStartupClasses, syntheticContextsToSyntheticClasses, appView);
      addParentClasses(
          clazz, rewrittenStartupClasses, syntheticContextsToSyntheticClasses, appView);
    }
  }

  private static void addSyntheticClassesAndParentClasses(
      DexProgramClass clazz,
      LinkedHashSet<DexType> rewrittenStartupClasses,
      Map<DexType, List<DexProgramClass>> syntheticContextsToSyntheticClasses,
      AppView<?> appView) {
    List<DexProgramClass> derivedClasses =
        syntheticContextsToSyntheticClasses.remove(clazz.getType());
    if (derivedClasses != null) {
      for (DexProgramClass derivedClass : derivedClasses) {
        addClassAndParentClasses(
            derivedClass, rewrittenStartupClasses, syntheticContextsToSyntheticClasses, appView);
      }
    }
  }

  private static void addParentClasses(
      DexProgramClass clazz,
      LinkedHashSet<DexType> rewrittenStartupClasses,
      Map<DexType, List<DexProgramClass>> syntheticContextsToSyntheticClasses,
      AppView<?> appView) {
    clazz.forEachImmediateSupertype(
        supertype ->
            addClassAndParentClasses(
                supertype, rewrittenStartupClasses, syntheticContextsToSyntheticClasses, appView));
  }

  @Override
  public StartupOrder withoutPrunedItems(PrunedItems prunedItems) {
    LinkedHashSet<DexType> rewrittenStartupClasses = new LinkedHashSet<>(startupClasses.size());
    for (DexType startupClass : startupClasses) {
      if (!prunedItems.isRemoved(startupClass)) {
        rewrittenStartupClasses.add(startupClass);
      }
    }
    return createNonEmpty(rewrittenStartupClasses);
  }

  private StartupOrder createNonEmpty(LinkedHashSet<DexType> startupClasses) {
    if (startupClasses.isEmpty()) {
      assert false;
      return empty();
    }
    return new NonEmptyStartupOrder(startupClasses);
  }
}
