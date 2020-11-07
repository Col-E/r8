// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Calculates the subtyping forrest for all classes. Unlike {@link
 * com.android.tools.r8.graph.SubtypingInfo}, interfaces are not included in this subtyping
 * information and only the immediate parents are stored (i.e. the transitive parents are not
 * calculated). In the following example graph, the roots are A, E and G, and each edge indicates an
 * entry in {@link SubtypingForrestForClasses#subtypeMap} going from the parent to an entry in the
 * collection of children. <code>
 *     A      E     G
 *    / \     |
 *   B  C     F
 *   |
 *   D
 * </code>
 */
public class SubtypingForrestForClasses {
  private final AppView<AppInfoWithLiveness> appView;

  private final Collection<DexProgramClass> roots = new ArrayList<>();
  private final Map<DexProgramClass, List<DexProgramClass>> subtypeMap = new IdentityHashMap<>();

  public SubtypingForrestForClasses(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    calculateSubtyping(appView.appInfo().classes());
  }

  private DexProgramClass superClass(DexProgramClass clazz) {
    return appView.programDefinitionFor(clazz.superType, clazz);
  }

  private void calculateSubtyping(Iterable<DexProgramClass> classes) {
    classes.forEach(this::calculateSubtyping);
  }

  private void calculateSubtyping(DexProgramClass clazz) {
    if (clazz.isInterface()) {
      return;
    }
    DexProgramClass superClass = superClass(clazz);
    if (superClass == null) {
      roots.add(clazz);
    } else {
      subtypeMap.computeIfAbsent(superClass, ignore -> new ArrayList<>()).add(clazz);
    }
  }

  public Collection<DexProgramClass> getProgramRoots() {
    return roots;
  }

  public Collection<DexProgramClass> getSubtypesFor(DexProgramClass clazz) {
    return subtypeMap.getOrDefault(clazz, Collections.emptyList());
  }

  public <T> T traverseNodeDepthFirst(
      DexProgramClass clazz, T state, BiFunction<DexProgramClass, T, T> consumer) {
    T newState = consumer.apply(clazz, state);
    getSubtypesFor(clazz).forEach(subClazz -> traverseNodeDepthFirst(subClazz, newState, consumer));
    return newState;
  }
}
