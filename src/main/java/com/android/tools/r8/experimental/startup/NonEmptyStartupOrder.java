// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.LazyBox;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NonEmptyStartupOrder extends StartupOrder {

  private final LinkedHashSet<StartupClass<DexType, DexMethod>> startupClasses;

  // Redundant sets to allow efficient querying without boxing.
  private final Set<DexType> nonSyntheticStartupClasses = Sets.newIdentityHashSet();
  private final Set<DexType> syntheticStartupClasses = Sets.newIdentityHashSet();

  NonEmptyStartupOrder(LinkedHashSet<StartupClass<DexType, DexMethod>> startupClasses) {
    assert !startupClasses.isEmpty();
    this.startupClasses = startupClasses;
    for (StartupClass<DexType, DexMethod> startupClass : startupClasses) {
      if (startupClass.isSynthetic()) {
        syntheticStartupClasses.add(startupClass.getReference());
      } else {
        nonSyntheticStartupClasses.add(startupClass.getReference());
      }
    }
  }

  @Override
  public boolean contains(DexType type, SyntheticItems syntheticItems) {
    return syntheticItems.isSyntheticClass(type)
        ? containsSyntheticClass(type, syntheticItems)
        : containsNonSyntheticClass(type);
  }

  private boolean containsNonSyntheticClass(DexType type) {
    return nonSyntheticStartupClasses.contains(type);
  }

  private boolean containsSyntheticClass(DexType type, SyntheticItems syntheticItems) {
    assert syntheticItems.isSyntheticClass(type);
    return Iterables.any(
        syntheticItems.getSynthesizingContextTypes(type),
        this::containsSyntheticClassesSynthesizedFrom);
  }

  private boolean containsSyntheticClassesSynthesizedFrom(DexType synthesizingContextType) {
    return syntheticStartupClasses.contains(synthesizingContextType);
  }

  @Override
  public Collection<StartupClass<DexType, DexMethod>> getClasses() {
    return startupClasses;
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public StartupOrder rewrittenWithLens(GraphLens graphLens) {
    LinkedHashSet<StartupClass<DexType, DexMethod>> rewrittenStartupClasses =
        new LinkedHashSet<>(startupClasses.size());
    for (StartupClass<DexType, DexMethod> startupClass : startupClasses) {
      rewrittenStartupClasses.add(
          StartupClass.dexBuilder()
              .setFlags(startupClass.getFlags())
              .setClassReference(graphLens.lookupType(startupClass.getReference()))
              .build());
    }
    return createNonEmpty(rewrittenStartupClasses);
  }

  @Override
  public StartupOrder toStartupOrderForWriting(AppView<?> appView) {
    LinkedHashSet<StartupClass<DexType, DexMethod>> rewrittenStartupClasses =
        new LinkedHashSet<>(startupClasses.size());
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
    for (StartupClass<DexType, DexMethod> startupClass : startupClasses) {
      addStartupClass(
          startupClass, rewrittenStartupClasses, syntheticContextsToSyntheticClasses, appView);
    }
    assert rewrittenStartupClasses.stream().noneMatch(StartupClass::isSynthetic);
    return createNonEmpty(rewrittenStartupClasses);
  }

  private static void addStartupClass(
      StartupClass<DexType, DexMethod> startupClass,
      LinkedHashSet<StartupClass<DexType, DexMethod>> rewrittenStartupClasses,
      Map<DexType, List<DexProgramClass>> syntheticContextsToSyntheticClasses,
      AppView<?> appView) {
    if (startupClass.isSynthetic()) {
      List<DexProgramClass> syntheticClassesForContext =
          syntheticContextsToSyntheticClasses.getOrDefault(
              startupClass.getReference(), Collections.emptyList());
      for (DexProgramClass clazz : syntheticClassesForContext) {
        addClassAndParentClasses(clazz, rewrittenStartupClasses, appView);
      }
    } else {
      addClassAndParentClasses(startupClass.getReference(), rewrittenStartupClasses, appView);
    }
  }

  private static boolean addClass(
      DexProgramClass clazz,
      LinkedHashSet<StartupClass<DexType, DexMethod>> rewrittenStartupClasses) {
    return rewrittenStartupClasses.add(
        StartupClass.dexBuilder().setClassReference(clazz.getType()).build());
  }

  private static void addClassAndParentClasses(
      DexType type,
      LinkedHashSet<StartupClass<DexType, DexMethod>> rewrittenStartupClasses,
      AppView<?> appView) {
    DexProgramClass definition = appView.app().programDefinitionFor(type);
    if (definition != null) {
      addClassAndParentClasses(definition, rewrittenStartupClasses, appView);
    }
  }

  private static void addClassAndParentClasses(
      DexProgramClass clazz,
      LinkedHashSet<StartupClass<DexType, DexMethod>> rewrittenStartupClasses,
      AppView<?> appView) {
    if (addClass(clazz, rewrittenStartupClasses)) {
      addParentClasses(clazz, rewrittenStartupClasses, appView);
    }
  }

  private static void addParentClasses(
      DexProgramClass clazz,
      LinkedHashSet<StartupClass<DexType, DexMethod>> rewrittenStartupClasses,
      AppView<?> appView) {
    clazz.forEachImmediateSupertype(
        supertype -> addClassAndParentClasses(supertype, rewrittenStartupClasses, appView));
  }

  @Override
  public StartupOrder withoutPrunedItems(PrunedItems prunedItems, SyntheticItems syntheticItems) {
    LinkedHashSet<StartupClass<DexType, DexMethod>> rewrittenStartupClasses =
        new LinkedHashSet<>(startupClasses.size());
    LazyBox<Set<DexType>> contextsOfLiveSynthetics =
        new LazyBox<>(
            () -> computeContextsOfLiveSynthetics(prunedItems.getPrunedApp(), syntheticItems));
    for (StartupClass<DexType, DexMethod> startupClass : startupClasses) {
      // Only prune non-synthetic classes, since the pruning of a class does not imply that all
      // classes synthesized from it have been pruned.
      if (startupClass.isSynthetic()) {
        if (contextsOfLiveSynthetics.computeIfAbsent().contains(startupClass.getReference())) {
          rewrittenStartupClasses.add(startupClass);
        }
      } else if (!prunedItems.isRemoved(startupClass.getReference())) {
        rewrittenStartupClasses.add(startupClass);
      }
    }
    return createNonEmpty(rewrittenStartupClasses);
  }

  private Set<DexType> computeContextsOfLiveSynthetics(
      DexApplication app, SyntheticItems syntheticItems) {
    Set<DexType> contextsOfLiveSynthetics = Sets.newIdentityHashSet();
    for (DexProgramClass clazz : app.classes()) {
      if (syntheticItems.isSyntheticClass(clazz)) {
        contextsOfLiveSynthetics.addAll(
            syntheticItems.getSynthesizingContextTypes(clazz.getType()));
      }
    }
    return contextsOfLiveSynthetics;
  }

  private StartupOrder createNonEmpty(
      LinkedHashSet<StartupClass<DexType, DexMethod>> startupClasses) {
    if (startupClasses.isEmpty()) {
      assert false;
      return empty();
    }
    return new NonEmptyStartupOrder(startupClasses);
  }
}
