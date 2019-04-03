// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Consumer;

public class TopDownClassHierarchyTraversal<T extends DexClass> {

  private enum Scope {
    ALL_CLASSES,
    ONLY_PROGRAM_CLASSES
  }

  private final DexDefinitionSupplier definitions;
  private final Scope scope;

  private final Set<T> visited = new HashSet<>();
  private final Deque<T> worklist = new ArrayDeque<>();

  private TopDownClassHierarchyTraversal(DexDefinitionSupplier definitions, Scope scope) {
    this.definitions = definitions;
    this.scope = scope;
  }

  /**
   * Returns a visitor that can be used to visit all the classes (including class path and library
   * classes) that are reachable from a given set of sources.
   */
  public static TopDownClassHierarchyTraversal<DexClass> forAllClasses(
      DexDefinitionSupplier definitions) {
    return new TopDownClassHierarchyTraversal<>(definitions, Scope.ALL_CLASSES);
  }

  /**
   * Returns a visitor that can be used to visit all the program classes that are reachable from a
   * given set of sources.
   */
  public static TopDownClassHierarchyTraversal<DexProgramClass> forProgramClasses(
      DexDefinitionSupplier definitions) {
    return new TopDownClassHierarchyTraversal<>(definitions, Scope.ONLY_PROGRAM_CLASSES);
  }

  public void visit(Iterable<DexProgramClass> sources, Consumer<T> visitor) {
    Iterator<DexProgramClass> sourceIterator = sources.iterator();

    // Visit the program classes in a top-down order according to the class hierarchy.
    while (sourceIterator.hasNext() || !worklist.isEmpty()) {
      if (worklist.isEmpty()) {
        // Add the ancestors of the next source (including the source itself) to the worklist in
        // such a way that all super types of the source class come before the class itself.
        addAncestorsToWorklist(sourceIterator.next());
        if (worklist.isEmpty()) {
          continue;
        }
      }

      T clazz = worklist.removeFirst();
      if (visited.add(clazz)) {
        assert scope != Scope.ONLY_PROGRAM_CLASSES || clazz.isProgramClass();
        visitor.accept(clazz);
      }
    }

    visited.clear();
  }

  private void addAncestorsToWorklist(DexClass clazz) {
    @SuppressWarnings("unchecked")
    T clazzWithTypeT = (T) clazz;

    if (visited.contains(clazzWithTypeT)) {
      return;
    }

    worklist.addFirst(clazzWithTypeT);

    // Add super classes to worklist.
    if (clazz.superType != null) {
      DexClass definition = definitions.definitionFor(clazz.superType);
      if (definition != null) {
        if (scope != Scope.ONLY_PROGRAM_CLASSES || definition.isProgramClass()) {
          addAncestorsToWorklist(definition);
        }
      }
    }

    // Add super interfaces to worklist.
    for (DexType interfaceType : clazz.interfaces.values) {
      DexClass definition = definitions.definitionFor(interfaceType);
      if (definition != null) {
        if (scope != Scope.ONLY_PROGRAM_CLASSES || definition.isProgramClass()) {
          addAncestorsToWorklist(definition);
        }
      }
    }
  }
}
