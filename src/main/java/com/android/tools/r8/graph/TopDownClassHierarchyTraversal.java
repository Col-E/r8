// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

public class TopDownClassHierarchyTraversal<T extends DexClass>
    extends ClassHierarchyTraversal<T, TopDownClassHierarchyTraversal<T>> {

  private TopDownClassHierarchyTraversal(
      AppView<? extends AppInfoWithSubtyping> appView, Scope scope) {
    super(appView, scope);
  }

  /**
   * Returns a visitor that can be used to visit all the classes (including class path and library
   * classes) that are reachable from a given set of sources.
   */
  public static TopDownClassHierarchyTraversal<DexClass> forAllClasses(
      AppView<? extends AppInfoWithSubtyping> appView) {
    return new TopDownClassHierarchyTraversal<>(appView, Scope.ALL_CLASSES);
  }

  /**
   * Returns a visitor that can be used to visit all the program classes that are reachable from a
   * given set of sources.
   */
  public static TopDownClassHierarchyTraversal<DexProgramClass> forProgramClasses(
      AppView<? extends AppInfoWithSubtyping> appView) {
    return new TopDownClassHierarchyTraversal<>(appView, Scope.ONLY_PROGRAM_CLASSES);
  }

  @Override
  TopDownClassHierarchyTraversal<T> self() {
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

    // Add super classes to worklist.
    if (clazz.superType != null) {
      DexClass definition = appView.definitionFor(clazz.superType);
      if (definition != null) {
        if (scope != Scope.ONLY_PROGRAM_CLASSES || definition.isProgramClass()) {
          addDependentsToWorklist(definition);
        }
      }
    }

    // Add super interfaces to worklist.
    if (!excludeInterfaces) {
      for (DexType interfaceType : clazz.interfaces.values) {
        DexClass definition = appView.definitionFor(interfaceType);
        if (definition != null) {
          if (scope != Scope.ONLY_PROGRAM_CLASSES || definition.isProgramClass()) {
            addDependentsToWorklist(definition);
          }
        }
      }
    }
  }
}
