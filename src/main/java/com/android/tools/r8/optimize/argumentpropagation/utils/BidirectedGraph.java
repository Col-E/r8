// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.utils;

import com.android.tools.r8.utils.WorkList;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public abstract class BidirectedGraph<T> {

  public abstract void forEachNeighbor(T node, Consumer<? super T> consumer);

  public abstract void forEachNode(Consumer<? super T> consumer);

  /**
   * Computes the strongly connected components in the current bidirectional graph (i.e., each
   * strongly connected component can be found using a breadth first search).
   */
  public List<Set<T>> computeStronglyConnectedComponents() {
    Set<T> seen = new HashSet<>();
    List<Set<T>> stronglyConnectedComponents = new ArrayList<>();
    forEachNode(
        node -> {
          if (seen.contains(node)) {
            return;
          }
          Set<T> stronglyConnectedComponent = internalComputeStronglyConnectedProgramClasses(node);
          stronglyConnectedComponents.add(stronglyConnectedComponent);
          seen.addAll(stronglyConnectedComponent);
        });
    return stronglyConnectedComponents;
  }

  private Set<T> internalComputeStronglyConnectedProgramClasses(T node) {
    WorkList<T> worklist = WorkList.newEqualityWorkList(node);
    while (worklist.hasNext()) {
      T current = worklist.next();
      forEachNeighbor(current, worklist::addIfNotSeen);
    }
    return worklist.getSeenSet();
  }
}
