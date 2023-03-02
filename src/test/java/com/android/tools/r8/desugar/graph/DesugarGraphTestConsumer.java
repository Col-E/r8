// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.graph;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.DesugarGraphConsumer;
import com.android.tools.r8.origin.GlobalSyntheticOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.StringUtils.BraceType;
import com.android.tools.r8.utils.WorkList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class DesugarGraphTestConsumer implements DesugarGraphConsumer {

  private boolean finished = false;

  // Set of all origins for the desugaring candidates in the compilation unit.
  private final Set<Origin> desugaringCompilationUnit = new HashSet<>();

  // Map from a dependency to its immediate dependents.
  private final Map<Origin, Set<Origin>> dependents = new HashMap<>();

  // Map from a dependent to its immedate dependencies.
  private Map<Origin, Set<Origin>> dependencies = null;

  @Override
  public String toString() {
    if (dependents.isEmpty()) {
      return "<empty>";
    }
    StringBuilder builder = new StringBuilder();
    dependents.forEach(
        (k, vs) ->
            StringUtils.append(builder.append(k).append(" -> "), vs, ", ", BraceType.TUBORG)
                .append("\n"));
    return builder.toString();
  }

  public Set<Origin> getDirectDependencies(Origin dependent) {
    return Collections.unmodifiableSet(
        dependencies.getOrDefault(dependent, Collections.emptySet()));
  }

  public Set<Origin> getDirectDependents(Origin dependency) {
    return Collections.unmodifiableSet(dependents.getOrDefault(dependency, Collections.emptySet()));
  }

  public Set<Origin> getTransitiveDependencies(Origin dependent) {
    return getTransitiveClosure(dependent, this::getDirectDependencies);
  }

  public Set<Origin> getTransitiveDependents(Origin dependency) {
    return getTransitiveClosure(dependency, this::getDirectDependents);
  }

  private static Set<Origin> getTransitiveClosure(
      Origin item, Function<Origin, Set<Origin>> edges) {
    WorkList<Origin> worklist = WorkList.newEqualityWorkList(edges.apply(item));
    while (worklist.hasNext()) {
      worklist.addIfNotSeen(edges.apply(worklist.next()));
    }
    return worklist.getSeenSet();
  }

  public boolean contains(Origin dependency, Origin dependent) {
    assertTrue(finished);
    return dependents.getOrDefault(dependency, Collections.emptySet()).contains(dependent);
  }

  public int totalEdgeCount() {
    assertTrue(finished);
    int count = 0;
    for (Set<Origin> dependents : dependents.values()) {
      count += dependents.size();
    }
    return count;
  }

  public Set<Origin> getDesugaringCompilationUnit() {
    assertTrue(finished);
    return desugaringCompilationUnit;
  }

  @Override
  public synchronized void acceptProgramNode(Origin node) {
    desugaringCompilationUnit.add(node);
  }

  @Override
  public synchronized void accept(Origin dependent, Origin dependency) {
    // D8/R8 should not report edges synthetic origin.
    assertNotEquals(dependent, GlobalSyntheticOrigin.instance());
    assertNotEquals(dependency, GlobalSyntheticOrigin.instance());
    // D8/R8 may report edges to unknown origin, but that is typically *not* what should be done.
    assertNotEquals(dependency, Origin.unknown());
    assertNotEquals(dependent, Origin.unknown());
    assertFalse(finished);
    dependents.computeIfAbsent(dependency, s -> new HashSet<>()).add(dependent);
  }

  @Override
  public void finished() {
    assertFalse(finished);
    finished = true;
    dependencies = new HashMap<>();
    dependents.forEach(
        (dependency, dependents) ->
            dependents.forEach(
                dependent ->
                    dependencies.computeIfAbsent(dependent, k -> new HashSet<>()).add(dependency)));
  }
}
