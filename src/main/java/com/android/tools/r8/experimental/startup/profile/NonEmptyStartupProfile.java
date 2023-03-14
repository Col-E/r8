// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup.profile;

import com.android.tools.r8.experimental.startup.StartupProfile;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.ThrowingConsumer;
import java.util.Collection;
import java.util.LinkedHashMap;

public class NonEmptyStartupProfile extends StartupProfile {

  private final LinkedHashMap<DexReference, StartupProfileRule> startupItems;

  public NonEmptyStartupProfile(LinkedHashMap<DexReference, StartupProfileRule> startupItems) {
    assert !startupItems.isEmpty();
    this.startupItems = startupItems;
  }

  @Override
  public boolean containsMethodRule(DexMethod method) {
    return startupItems.containsKey(method);
  }

  @Override
  public boolean containsClassRule(DexType type) {
    return startupItems.containsKey(type);
  }

  @Override
  public <E1 extends Exception, E2 extends Exception> void forEachRule(
      ThrowingConsumer<StartupProfileClassRule, E1> classRuleConsumer,
      ThrowingConsumer<StartupProfileMethodRule, E2> methodRuleConsumer)
      throws E1, E2 {
    for (StartupProfileRule rule : getRules()) {
      rule.accept(classRuleConsumer, methodRuleConsumer);
    }
  }

  @Override
  public StartupProfileClassRule getClassRule(DexType type) {
    return (StartupProfileClassRule) startupItems.get(type);
  }

  @Override
  public StartupProfileMethodRule getMethodRule(DexMethod method) {
    return (StartupProfileMethodRule) startupItems.get(method);
  }

  @Override
  public Collection<StartupProfileRule> getRules() {
    return startupItems.values();
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public StartupProfile rewrittenWithLens(GraphLens graphLens) {
    LinkedHashMap<DexReference, StartupProfileRule> rewrittenStartupItems =
        new LinkedHashMap<>(startupItems.size());
    for (StartupProfileRule startupItem : startupItems.values()) {
      // TODO(b/271822426): This should account for one-to-many mappings. e.g., when a bridge is
      //  created.
      startupItem.apply(
          startupClass ->
              rewrittenStartupItems.put(
                  startupClass.getReference(),
                  StartupProfileClassRule.builder()
                      .setClassReference(graphLens.lookupType(startupClass.getReference()))
                      .build()),
          startupMethod ->
              rewrittenStartupItems.put(
                  startupMethod.getReference(),
                  StartupProfileMethodRule.builder()
                      .setMethod(graphLens.getRenamedMethodSignature(startupMethod.getReference()))
                      .build()));
    }
    return createNonEmpty(rewrittenStartupItems);
  }

  public int size() {
    return startupItems.size();
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
  public StartupProfile toStartupOrderForWriting(AppView<?> appView) {
    LinkedHashMap<DexReference, StartupProfileRule> rewrittenStartupItems =
        new LinkedHashMap<>(startupItems.size());
    for (StartupProfileRule startupItem : startupItems.values()) {
      addStartupItem(startupItem, rewrittenStartupItems, appView);
    }
    return createNonEmpty(rewrittenStartupItems);
  }

  private static void addStartupItem(
      StartupProfileRule startupItem,
      LinkedHashMap<DexReference, StartupProfileRule> rewrittenStartupItems,
      AppView<?> appView) {
    startupItem.accept(
        startupClass ->
            addClassAndParentClasses(startupClass.getReference(), rewrittenStartupItems, appView),
        startupMethod -> rewrittenStartupItems.put(startupItem.getReference(), startupItem));
  }

  private static boolean addClass(
      DexProgramClass clazz,
      LinkedHashMap<DexReference, StartupProfileRule> rewrittenStartupItems) {
    StartupProfileRule previous =
        rewrittenStartupItems.put(
            clazz.getType(),
            StartupProfileClassRule.builder().setClassReference(clazz.getType()).build());
    return previous == null;
  }

  private static void addClassAndParentClasses(
      DexType type,
      LinkedHashMap<DexReference, StartupProfileRule> rewrittenStartupItems,
      AppView<?> appView) {
    DexProgramClass definition = appView.app().programDefinitionFor(type);
    if (definition != null) {
      addClassAndParentClasses(definition, rewrittenStartupItems, appView);
    }
  }

  private static void addClassAndParentClasses(
      DexProgramClass clazz,
      LinkedHashMap<DexReference, StartupProfileRule> rewrittenStartupItems,
      AppView<?> appView) {
    if (addClass(clazz, rewrittenStartupItems)) {
      addParentClasses(clazz, rewrittenStartupItems, appView);
    }
  }

  private static void addParentClasses(
      DexProgramClass clazz,
      LinkedHashMap<DexReference, StartupProfileRule> rewrittenStartupItems,
      AppView<?> appView) {
    clazz.forEachImmediateSupertype(
        supertype -> addClassAndParentClasses(supertype, rewrittenStartupItems, appView));
  }

  @Override
  public StartupProfile withoutPrunedItems(PrunedItems prunedItems, SyntheticItems syntheticItems) {
    LinkedHashMap<DexReference, StartupProfileRule> rewrittenStartupItems =
        new LinkedHashMap<>(startupItems.size());
    for (StartupProfileRule startupItem : startupItems.values()) {
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

  private StartupProfile createNonEmpty(
      LinkedHashMap<DexReference, StartupProfileRule> startupItems) {
    if (startupItems.isEmpty()) {
      assert false;
      return empty();
    }
    return new NonEmptyStartupProfile(startupItems);
  }
}
