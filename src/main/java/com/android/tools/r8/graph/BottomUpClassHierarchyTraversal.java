// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import java.util.function.Function;

public class BottomUpClassHierarchyTraversal<T extends DexClass>
    extends ClassHierarchyTraversal<T, BottomUpClassHierarchyTraversal<T>> {

  private final Function<DexType, Iterable<DexType>> immediateSubtypesProvider;

  private BottomUpClassHierarchyTraversal(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Function<DexType, Iterable<DexType>> immediateSubtypesProvider,
      Scope scope) {
    super(appView, scope);
    this.immediateSubtypesProvider = immediateSubtypesProvider;
  }

  /**
   * Returns a visitor that can be used to visit all the classes (including class path and library
   * classes) that are reachable from a given set of sources.
   */
  public static BottomUpClassHierarchyTraversal<DexClass> forAllClasses(
      AppView<? extends AppInfoWithClassHierarchy> appView, SubtypingInfo subtypingInfo) {
    return new BottomUpClassHierarchyTraversal<>(
        appView, subtypingInfo::allImmediateSubtypes, Scope.ALL_CLASSES);
  }

  /**
   * Returns a visitor that can be used to visit all the program classes that are reachable from a
   * given set of sources.
   */
  public static BottomUpClassHierarchyTraversal<DexProgramClass> forProgramClasses(
      AppView<? extends AppInfoWithClassHierarchy> appView, SubtypingInfo subtypingInfo) {
    return forProgramClasses(appView, subtypingInfo::allImmediateSubtypes);
  }

  /**
   * Returns a visitor that can be used to visit all the program classes that are reachable from a
   * given set of sources.
   */
  public static BottomUpClassHierarchyTraversal<DexProgramClass> forProgramClasses(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Function<DexType, Iterable<DexType>> immediateSubtypesProvider) {
    return new BottomUpClassHierarchyTraversal<>(
        appView, immediateSubtypesProvider, Scope.ONLY_PROGRAM_CLASSES);
  }

  @Override
  BottomUpClassHierarchyTraversal<T> self() {
    return this;
  }

  @Override
  void addDependentsToWorklist(DexClass clazz) {
    @SuppressWarnings("unchecked")
    T clazzWithTypeT = (T) clazz;

    if (excludeInterfaces && clazzWithTypeT.isInterface()) {
      return;
    }

    if (visited.contains(clazzWithTypeT)) {
      return;
    }

    worklist.addFirst(clazzWithTypeT);

    // Add subtypes to worklist.
    for (DexType subtype : immediateSubtypesProvider.apply(clazz.getType())) {
      DexClass definition = definitionSupplier.contextIndependentDefinitionFor(subtype);
      if (definition != null) {
        if (scope != Scope.ONLY_PROGRAM_CLASSES || definition.isProgramClass()) {
          addDependentsToWorklist(definition);
        }
      }
    }
  }
}
