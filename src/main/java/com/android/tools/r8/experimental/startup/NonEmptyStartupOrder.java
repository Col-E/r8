// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.LazyBox;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NonEmptyStartupOrder extends StartupOrder {

  private final LinkedHashSet<StartupItem<DexType, DexMethod, ?>> startupItems;

  // Sets to allow efficient querying without boxing.
  private final Set<DexType> nonSyntheticStartupClasses = Sets.newIdentityHashSet();
  private final Set<DexType> syntheticStartupClasses = Sets.newIdentityHashSet();

  NonEmptyStartupOrder(LinkedHashSet<StartupItem<DexType, DexMethod, ?>> startupItems) {
    assert !startupItems.isEmpty();
    this.startupItems = startupItems;
    for (StartupItem<DexType, DexMethod, ?> startupItem : startupItems) {
      if (startupItem.isSynthetic()) {
        assert startupItem.isStartupClass();
        syntheticStartupClasses.add(startupItem.asStartupClass().getReference());
      } else {
        DexReference reference =
            startupItem.apply(StartupClass::getReference, StartupMethod::getReference);
        nonSyntheticStartupClasses.add(reference.getContextType());
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
  public Collection<StartupItem<DexType, DexMethod, ?>> getItems() {
    return startupItems;
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public StartupOrder rewrittenWithLens(GraphLens graphLens) {
    LinkedHashSet<StartupItem<DexType, DexMethod, ?>> rewrittenStartupItems =
        new LinkedHashSet<>(startupItems.size());
    for (StartupItem<DexType, DexMethod, ?> startupItem : startupItems) {
      if (startupItem.isStartupClass()) {
        StartupClass<DexType, DexMethod> startupClass = startupItem.asStartupClass();
        rewrittenStartupItems.add(
            StartupClass.dexBuilder()
                .setClassReference(graphLens.lookupType(startupClass.getReference()))
                .setSynthetic(startupItem.isSynthetic())
                .build());
      } else {
        assert !startupItem.isSynthetic();
        StartupMethod<DexType, DexMethod> startupMethod = startupItem.asStartupMethod();
        // TODO(b/238173796): This should account for one-to-many mappings. e.g., when a bridge is
        //  created.
        rewrittenStartupItems.add(
            StartupMethod.dexBuilder()
                .setMethodReference(
                    graphLens.getRenamedMethodSignature(startupMethod.getReference()))
                .build());
      }
    }
    return createNonEmpty(rewrittenStartupItems);
  }

  /**
   * This is called to process the startup order before computing the startup layouts.
   *
   * <p>This processing makes two key changes to the startup order:
   *
   * <ul>
   *   <li>Synthetic startup classes on the form "SLcom/example/SyntheticContext;" represents that
   *       any method of any synthetic class that have been synthesized from SyntheticContext has
   *       been executed. This pass removes such entries from the startup order, and replaces them
   *       by all the methods from all of the synthetics that have been synthesized from
   *       SyntheticContext.
   *   <li>Moreover, this inserts a StartupClass event for all supertypes of a given class next to
   *       the class in the startup order. This ensures that the classes from the super hierarchy
   *       will be laid out close to their subclasses, at the point where the subclasses are used
   *       during startup.
   *       <p>Note that this normally follows from the trace already, except that the class
   *       initializers of interfaces are not executed when a subclass is used.
   * </ul>
   */
  @Override
  public StartupOrder toStartupOrderForWriting(AppView<?> appView) {
    LinkedHashSet<StartupItem<DexType, DexMethod, ?>> rewrittenStartupItems =
        new LinkedHashSet<>(startupItems.size());
    Map<DexType, List<DexProgramClass>> syntheticContextsToSyntheticClasses =
        appView.getSyntheticItems().computeSyntheticContextsToSyntheticClasses(appView);
    for (StartupItem<DexType, DexMethod, ?> startupItem : startupItems) {
      addStartupItem(
          startupItem, rewrittenStartupItems, syntheticContextsToSyntheticClasses, appView);
    }
    assert rewrittenStartupItems.stream().noneMatch(StartupItem::isSynthetic);
    return createNonEmpty(rewrittenStartupItems);
  }

  private static void addStartupItem(
      StartupItem<DexType, DexMethod, ?> startupItem,
      LinkedHashSet<StartupItem<DexType, DexMethod, ?>> rewrittenStartupItems,
      Map<DexType, List<DexProgramClass>> syntheticContextsToSyntheticClasses,
      AppView<?> appView) {
    if (startupItem.isSynthetic()) {
      assert startupItem.isStartupClass();
      StartupClass<DexType, DexMethod> startupClass = startupItem.asStartupClass();
      List<DexProgramClass> syntheticClassesForContext =
          syntheticContextsToSyntheticClasses.getOrDefault(
              startupClass.getReference(), Collections.emptyList());
      for (DexProgramClass clazz : syntheticClassesForContext) {
        addClassAndParentClasses(clazz, rewrittenStartupItems, appView);
        addAllMethods(clazz, rewrittenStartupItems);
      }
    } else {
      if (startupItem.isStartupClass()) {
        addClassAndParentClasses(
            startupItem.asStartupClass().getReference(), rewrittenStartupItems, appView);
      } else {
        rewrittenStartupItems.add(startupItem);
      }
    }
  }

  private static boolean addClass(
      DexProgramClass clazz,
      LinkedHashSet<StartupItem<DexType, DexMethod, ?>> rewrittenStartupItems) {
    return rewrittenStartupItems.add(
        StartupClass.dexBuilder().setClassReference(clazz.getType()).build());
  }

  private static void addClassAndParentClasses(
      DexType type,
      LinkedHashSet<StartupItem<DexType, DexMethod, ?>> rewrittenStartupItems,
      AppView<?> appView) {
    DexProgramClass definition = appView.app().programDefinitionFor(type);
    if (definition != null) {
      addClassAndParentClasses(definition, rewrittenStartupItems, appView);
    }
  }

  private static void addClassAndParentClasses(
      DexProgramClass clazz,
      LinkedHashSet<StartupItem<DexType, DexMethod, ?>> rewrittenStartupItems,
      AppView<?> appView) {
    if (addClass(clazz, rewrittenStartupItems)) {
      addParentClasses(clazz, rewrittenStartupItems, appView);
    }
  }

  private static void addParentClasses(
      DexProgramClass clazz,
      LinkedHashSet<StartupItem<DexType, DexMethod, ?>> rewrittenStartupItems,
      AppView<?> appView) {
    clazz.forEachImmediateSupertype(
        supertype -> addClassAndParentClasses(supertype, rewrittenStartupItems, appView));
  }

  private static void addAllMethods(
      DexProgramClass clazz,
      LinkedHashSet<StartupItem<DexType, DexMethod, ?>> rewrittenStartupItems) {
    clazz.forEachProgramMethod(
        method ->
            rewrittenStartupItems.add(
                StartupMethod.dexBuilder().setMethodReference(method.getReference()).build()));
  }

  @Override
  public StartupOrder withoutPrunedItems(PrunedItems prunedItems, SyntheticItems syntheticItems) {
    LinkedHashSet<StartupItem<DexType, DexMethod, ?>> rewrittenStartupItems =
        new LinkedHashSet<>(startupItems.size());
    LazyBox<Set<DexType>> contextsOfLiveSynthetics =
        new LazyBox<>(
            () -> computeContextsOfLiveSynthetics(prunedItems.getPrunedApp(), syntheticItems));
    for (StartupItem<DexType, DexMethod, ?> startupItem : startupItems) {
      // Only prune non-synthetic classes, since the pruning of a class does not imply that all
      // classes synthesized from it have been pruned.
      if (startupItem.isSynthetic()) {
        assert startupItem.isStartupClass();
        StartupClass<DexType, DexMethod> startupClass = startupItem.asStartupClass();
        if (contextsOfLiveSynthetics.computeIfAbsent().contains(startupClass.getReference())) {
          rewrittenStartupItems.add(startupClass);
        }
      } else {
        DexReference reference =
            startupItem.apply(StartupClass::getReference, StartupMethod::getReference);
        if (!prunedItems.isRemoved(reference)) {
          rewrittenStartupItems.add(startupItem);
        }
      }
    }
    return createNonEmpty(rewrittenStartupItems);
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
      LinkedHashSet<StartupItem<DexType, DexMethod, ?>> startupItems) {
    if (startupItems.isEmpty()) {
      assert false;
      return empty();
    }
    return new NonEmptyStartupOrder(startupItems);
  }
}
