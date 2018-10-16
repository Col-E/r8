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

public class TopDownClassHierarchyTraversal {

  public static void visit(
      AppView<? extends AppInfo> appView,
      Iterable<DexProgramClass> classes,
      Consumer<DexProgramClass> visitor) {
    Deque<DexProgramClass> worklist = new ArrayDeque<>();
    Set<DexProgramClass> visited = new HashSet<>();

    Iterator<DexProgramClass> classIterator = classes.iterator();

    // Visit the program classes in a top-down order according to the class hierarchy.
    while (classIterator.hasNext() || !worklist.isEmpty()) {
      if (worklist.isEmpty()) {
        // Add the ancestors of this class (including the class itself) to the worklist in such a
        // way that all super types of the class come before the class itself.
        addAncestorsToWorklist(classIterator.next(), worklist, visited, appView);
        if (worklist.isEmpty()) {
          continue;
        }
      }

      DexProgramClass clazz = worklist.removeFirst();
      if (visited.add(clazz)) {
        visitor.accept(clazz);
      }
    }
  }

  private static void addAncestorsToWorklist(
      DexProgramClass clazz,
      Deque<DexProgramClass> worklist,
      Set<DexProgramClass> visited,
      AppView<? extends AppInfo> appView) {
    if (visited.contains(clazz)) {
      return;
    }

    worklist.addFirst(clazz);

    // Add super classes to worklist.
    if (clazz.superType != null) {
      DexClass definition = appView.appInfo().definitionFor(clazz.superType);
      if (definition != null && definition.isProgramClass()) {
        addAncestorsToWorklist(definition.asProgramClass(), worklist, visited, appView);
      }
    }

    // Add super interfaces to worklist.
    for (DexType interfaceType : clazz.interfaces.values) {
      DexClass definition = appView.appInfo().definitionFor(interfaceType);
      if (definition != null && definition.isProgramClass()) {
        addAncestorsToWorklist(definition.asProgramClass(), worklist, visited, appView);
      }
    }
  }
}
