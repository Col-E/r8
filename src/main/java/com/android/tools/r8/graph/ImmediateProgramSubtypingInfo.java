// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;
import static com.google.common.base.Predicates.alwaysTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ImmediateProgramSubtypingInfo {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final Map<DexProgramClass, List<DexProgramClass>> immediateSubtypes;

  private ImmediateProgramSubtypingInfo(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Map<DexProgramClass, List<DexProgramClass>> immediateSubtypes) {
    this.appView = appView;
    this.immediateSubtypes = immediateSubtypes;
  }

  public static ImmediateProgramSubtypingInfo create(
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    return internalCreate(appView, appView.appInfo().classes());
  }

  public static ImmediateProgramSubtypingInfo createWithDeterministicOrder(
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    return internalCreate(appView, appView.appInfo().classesWithDeterministicOrder());
  }

  private static ImmediateProgramSubtypingInfo internalCreate(
      AppView<? extends AppInfoWithClassHierarchy> appView, Collection<DexProgramClass> classes) {
    Map<DexProgramClass, List<DexProgramClass>> immediateSubtypes = new IdentityHashMap<>();
    for (DexProgramClass clazz : classes) {
      clazz.forEachImmediateSupertype(
          supertype -> {
            DexProgramClass superclass = asProgramClassOrNull(appView.definitionFor(supertype));
            if (superclass != null) {
              immediateSubtypes.computeIfAbsent(superclass, ignoreKey(ArrayList::new)).add(clazz);
            }
          });
    }
    return new ImmediateProgramSubtypingInfo(appView, immediateSubtypes);
  }

  public void forEachImmediateSuperClass(DexClass clazz, Consumer<? super DexClass> consumer) {
    forEachImmediateSuperClassMatching(
        clazz,
        (supertype, superclass) -> superclass != null,
        (supertype, superclass) -> consumer.accept(superclass));
  }

  public void forEachImmediateSuperClass(
      DexClass clazz, BiConsumer<? super DexType, ? super DexClass> consumer) {
    forEachImmediateSuperClassMatching(clazz, (supertype, superclass) -> true, consumer);
  }

  public void forEachImmediateSuperClassMatching(
      DexClass clazz,
      BiPredicate<? super DexType, ? super DexClass> predicate,
      BiConsumer<? super DexType, ? super DexClass> consumer) {
    clazz.forEachImmediateSupertype(
        supertype -> {
          DexClass superclass = appView.definitionFor(supertype);
          if (predicate.test(supertype, superclass)) {
            consumer.accept(supertype, superclass);
          }
        });
  }

  public void forEachImmediateSuperClassMatching(
      DexClass clazz, Predicate<? super DexClass> predicate, Consumer<? super DexClass> consumer) {
    clazz.forEachImmediateSupertype(
        supertype -> {
          DexClass superclass = appView.definitionFor(supertype);
          if (superclass != null && predicate.test(superclass)) {
            consumer.accept(superclass);
          }
        });
  }

  public void forEachImmediateProgramSuperClass(
      DexProgramClass clazz, Consumer<? super DexProgramClass> consumer) {
    forEachImmediateProgramSuperClassMatching(clazz, alwaysTrue(), consumer);
  }

  public void forEachImmediateProgramSuperClassMatching(
      DexProgramClass clazz,
      Predicate<? super DexProgramClass> predicate,
      Consumer<? super DexProgramClass> consumer) {
    clazz.forEachImmediateSupertype(
        supertype -> {
          DexProgramClass superclass = asProgramClassOrNull(appView.definitionFor(supertype));
          if (superclass != null && predicate.test(superclass)) {
            consumer.accept(superclass);
          }
        });
  }

  public void forEachImmediateSubClassMatching(
      DexProgramClass clazz,
      Predicate<? super DexProgramClass> predicate,
      Consumer<? super DexProgramClass> consumer) {
    getSubclasses(clazz)
        .forEach(
            subclass -> {
              if (predicate.test(subclass)) {
                consumer.accept(subclass);
              }
            });
  }

  public List<DexProgramClass> getSubclasses(DexProgramClass clazz) {
    return immediateSubtypes.getOrDefault(clazz, Collections.emptyList());
  }
}
