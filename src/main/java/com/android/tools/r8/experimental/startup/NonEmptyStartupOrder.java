// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup;

import com.android.tools.r8.experimental.startup.profile.StartupClass;
import com.android.tools.r8.experimental.startup.profile.StartupItem;
import com.android.tools.r8.experimental.startup.profile.StartupMethod;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.synthesis.SyntheticItems;
import java.util.Collection;
import java.util.LinkedHashMap;

public class NonEmptyStartupOrder extends StartupOrder {

  private final LinkedHashMap<DexReference, StartupItem> startupItems;

  NonEmptyStartupOrder(LinkedHashMap<DexReference, StartupItem> startupItems) {
    assert !startupItems.isEmpty();
    this.startupItems = startupItems;
  }

  @Override
  public boolean contains(DexMethod method) {
    return startupItems.containsKey(method);
  }

  @Override
  public boolean contains(DexType type) {
    return startupItems.containsKey(type);
  }

  @Override
  public Collection<StartupItem> getItems() {
    return startupItems.values();
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public StartupOrder rewrittenWithLens(GraphLens graphLens) {
    LinkedHashMap<DexReference, StartupItem> rewrittenStartupItems =
        new LinkedHashMap<>(startupItems.size());
    for (StartupItem startupItem : startupItems.values()) {
      // TODO(b/271822426): This should account for one-to-many mappings. e.g., when a bridge is
      //  created.
      startupItem.apply(
          startupClass ->
              rewrittenStartupItems.put(
                  startupClass.getReference(),
                  StartupClass.builder()
                      .setClassReference(graphLens.lookupType(startupClass.getReference()))
                      .build()),
          startupMethod ->
              rewrittenStartupItems.put(
                  startupMethod.getReference(),
                  StartupMethod.builder()
                      .setMethodReference(
                          graphLens.getRenamedMethodSignature(startupMethod.getReference()))
                      .build()));
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
    LinkedHashMap<DexReference, StartupItem> rewrittenStartupItems =
        new LinkedHashMap<>(startupItems.size());
    for (StartupItem startupItem : startupItems.values()) {
      addStartupItem(startupItem, rewrittenStartupItems, appView);
    }
    return createNonEmpty(rewrittenStartupItems);
  }

  private static void addStartupItem(
      StartupItem startupItem,
      LinkedHashMap<DexReference, StartupItem> rewrittenStartupItems,
      AppView<?> appView) {
    startupItem.accept(
        startupClass ->
            addClassAndParentClasses(startupClass.getReference(), rewrittenStartupItems, appView),
        startupMethod -> rewrittenStartupItems.put(startupItem.getReference(), startupItem));
  }

  private static boolean addClass(
      DexProgramClass clazz, LinkedHashMap<DexReference, StartupItem> rewrittenStartupItems) {
    StartupItem previous =
        rewrittenStartupItems.put(
            clazz.getType(), StartupClass.builder().setClassReference(clazz.getType()).build());
    return previous == null;
  }

  private static void addClassAndParentClasses(
      DexType type,
      LinkedHashMap<DexReference, StartupItem> rewrittenStartupItems,
      AppView<?> appView) {
    DexProgramClass definition = appView.app().programDefinitionFor(type);
    if (definition != null) {
      addClassAndParentClasses(definition, rewrittenStartupItems, appView);
    }
  }

  private static void addClassAndParentClasses(
      DexProgramClass clazz,
      LinkedHashMap<DexReference, StartupItem> rewrittenStartupItems,
      AppView<?> appView) {
    if (addClass(clazz, rewrittenStartupItems)) {
      addParentClasses(clazz, rewrittenStartupItems, appView);
    }
  }

  private static void addParentClasses(
      DexProgramClass clazz,
      LinkedHashMap<DexReference, StartupItem> rewrittenStartupItems,
      AppView<?> appView) {
    clazz.forEachImmediateSupertype(
        supertype -> addClassAndParentClasses(supertype, rewrittenStartupItems, appView));
  }

  @Override
  public StartupOrder withoutPrunedItems(PrunedItems prunedItems, SyntheticItems syntheticItems) {
    LinkedHashMap<DexReference, StartupItem> rewrittenStartupItems =
        new LinkedHashMap<>(startupItems.size());
    for (StartupItem startupItem : startupItems.values()) {
      // Only prune non-synthetic classes, since the pruning of a class does not imply that all
      // classes synthesized from it have been pruned.
      startupItem.accept(
          startupClass -> {
            if (!prunedItems.isRemoved(startupClass.getReference())) {
              rewrittenStartupItems.put(startupClass.getReference(), startupItem);
            }
          },
          startupMethod -> {
            if (!prunedItems.isRemoved(startupMethod.getReference())) {
              rewrittenStartupItems.put(startupMethod.getReference(), startupItem);
            }
          });
    }
    return createNonEmpty(rewrittenStartupItems);
  }

  private StartupOrder createNonEmpty(LinkedHashMap<DexReference, StartupItem> startupItems) {
    if (startupItems.isEmpty()) {
      assert false;
      return empty();
    }
    return new NonEmptyStartupOrder(startupItems);
  }
}
