// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.utils;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public abstract class DepthFirstTopDownClassHierarchyTraversal {

  // The state of a given class in the top-down traversal.
  private enum TraversalState {
    // Represents that a given class and all of its direct and indirect supertypes have been
    // visited by the top-down traversal, but all of the direct and indirect subtypes are still
    // not visited.
    SEEN,
    // Represents that a given class and all of its direct and indirect subtypes have been visited.
    // Such nodes will never be seen again in the top-down traversal, and any state stored for
    // such nodes can be pruned.
    FINISHED
  }

  protected final AppView<AppInfoWithLiveness> appView;
  protected final ImmediateProgramSubtypingInfo immediateSubtypingInfo;

  // Contains the traversal state for each class. If a given class is not in the map the class is
  // not yet seen.
  private final Map<DexProgramClass, TraversalState> states = new IdentityHashMap<>();

  // The class hierarchy is not a tree, thus for completeness we need to process all parent
  // interfaces for a given class or interface before continuing the top-down traversal. When the
  // top-down traversal for a given root returns, this means that there may be interfaces that are
  // seen but not finished. These interfaces are added to this collection such that we can
  // prioritize them over classes or interfaces that are yet not seen. This leads to more efficient
  // state pruning, since the state for these interfaces can be pruned when they transition to being
  // finished.
  //
  // See also prioritizeNewlySeenButNotFinishedRoots().
  private final List<DexProgramClass> newlySeenButNotFinishedRoots = new ArrayList<>();

  public DepthFirstTopDownClassHierarchyTraversal(
      AppView<AppInfoWithLiveness> appView, ImmediateProgramSubtypingInfo immediateSubtypingInfo) {
    this.appView = appView;
    this.immediateSubtypingInfo = immediateSubtypingInfo;
  }

  public abstract void visit(DexProgramClass clazz);

  public abstract void prune(DexProgramClass clazz);

  public void run(Collection<DexProgramClass> stronglyConnectedComponent) {
    // Perform a top-down traversal from each root in the strongly connected component.
    Deque<DexProgramClass> roots = computeRoots(stronglyConnectedComponent);
    while (!roots.isEmpty()) {
      DexProgramClass root = roots.removeLast();
      traverse(root);
      prioritizeNewlySeenButNotFinishedRoots(roots);
    }
  }

  private Deque<DexProgramClass> computeRoots(
      Collection<DexProgramClass> stronglyConnectedComponent) {
    Deque<DexProgramClass> roots = new ArrayDeque<>();
    for (DexProgramClass clazz : stronglyConnectedComponent) {
      if (isRoot(clazz)) {
        roots.add(clazz);
      }
    }
    return roots;
  }

  public boolean isRoot(DexProgramClass clazz) {
    if (clazz.hasSuperType()) {
      DexType superType = clazz.getSuperType();
      DexProgramClass superClass = asProgramClassOrNull(appView.definitionFor(superType));
      if (superClass != null) {
        return false;
      }
    }
    for (DexType implementedType : clazz.getInterfaces()) {
      DexProgramClass implementedClass =
          asProgramClassOrNull(appView.definitionFor(implementedType));
      if (implementedClass != null) {
        return false;
      }
    }
    return true;
  }

  private void prioritizeNewlySeenButNotFinishedRoots(Deque<DexProgramClass> roots) {
    assert newlySeenButNotFinishedRoots.stream()
        .allMatch(
            newlySeenButNotFinishedRoot -> {
              assert isRoot(newlySeenButNotFinishedRoot);
              assert isClassSeenButNotFinished(newlySeenButNotFinishedRoot);
              return true;
            });
    // Prioritize this class over other not yet seen classes. This leads to more efficient state
    // pruning.
    roots.addAll(newlySeenButNotFinishedRoots);
    newlySeenButNotFinishedRoots.clear();
  }

  private void traverse(DexProgramClass clazz) {
    // Check it the class and all of its subtypes are already processed.
    if (isClassFinished(clazz)) {
      return;
    }

    // Before continuing the top-down traversal, ensure that all super interfaces are processed,
    // but without visiting the entire subtree of each super interface.
    if (!isClassSeenButNotFinished(clazz)) {
      processSuperClasses(clazz);
      processClass(clazz);
    }

    processSubclasses(clazz);
    markFinished(clazz);
  }

  private void processSuperClasses(DexProgramClass clazz) {
    assert !isClassSeenButNotFinished(clazz);
    assert !isClassFinished(clazz);
    immediateSubtypingInfo.forEachImmediateProgramSuperClassMatching(
        clazz,
        superclass -> !isClassSeenButNotFinished(superclass),
        superclass -> {
          assert isClassUnseen(superclass);
          processSuperClasses(superclass);
          processClass(superclass);

          // If this is a root, then record that this root is seen but not finished.
          if (isRoot(superclass)) {
            newlySeenButNotFinishedRoots.add(superclass);
          }
        });
  }

  private void processSubclasses(DexProgramClass clazz) {
    forEachSubClass(clazz, this::traverse);
  }

  public void forEachSubClass(DexProgramClass clazz, Consumer<DexProgramClass> consumer) {
    immediateSubtypingInfo.getSubclasses(clazz).forEach(consumer);
  }

  private void processClass(DexProgramClass clazz) {
    assert !isClassSeenButNotFinished(clazz);
    assert !isClassFinished(clazz);
    visit(clazz);
    markSeenButNotFinished(clazz);
  }

  protected boolean isClassUnseen(DexProgramClass clazz) {
    return !states.containsKey(clazz);
  }

  protected boolean isClassSeenButNotFinished(DexProgramClass clazz) {
    return states.get(clazz) == TraversalState.SEEN;
  }

  protected boolean isClassFinished(DexProgramClass clazz) {
    return states.get(clazz) == TraversalState.FINISHED;
  }

  private void markSeenButNotFinished(DexProgramClass clazz) {
    assert isClassUnseen(clazz);
    states.put(clazz, TraversalState.SEEN);
  }

  private void markFinished(DexProgramClass clazz) {
    assert isClassSeenButNotFinished(clazz);
    states.put(clazz, TraversalState.FINISHED);
    prune(clazz);
  }
}
