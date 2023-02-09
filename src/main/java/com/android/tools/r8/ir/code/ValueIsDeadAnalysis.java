// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.optimize.DeadCodeRemover.DeadInstructionResult;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.MapUtils;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ValueIsDeadAnalysis {

  private enum ValueIsDeadResult {
    DEAD,
    NOT_DEAD;

    boolean isDead() {
      return this == DEAD;
    }

    boolean isNotDead() {
      return this == NOT_DEAD;
    }
  }

  private final AppView<?> appView;
  private final IRCode code;

  private final Map<Value, ValueIsDeadResult> analysisCache = new IdentityHashMap<>();

  public ValueIsDeadAnalysis(AppView<?> appView, IRCode code) {
    this.appView = appView;
    this.code = code;
  }

  public boolean isDead(Value value) {
    // Totally unused values are trivially dead.
    if (value.isUnused()) {
      return true;
    }
    // Create an is-dead dependence graph. If the deadness of value v depends on value u being dead,
    // a directed edge [v -> u] is added to the graph.
    //
    // This graph serves two purposes:
    // 1) If the analysis finds that `u` is *not* dead, then using the graph we can find all the
    //    values whose deadness depend (directly or indirectly) on `u` being dead, and mark them as
    //    being *not* dead in the analysis cache.
    // 2) If the analysis finds that `u` *is* dead, we can remove the node from the dependence graph
    //    (as it is necessarily a leaf), and repeatedly mark direct and indirect predecessors of `u`
    //    that have now become leaves as being dead in the analysis cache.
    WorkList<Value> worklist = WorkList.newIdentityWorkList(value);
    BooleanBox foundCycle = new BooleanBox();
    Value notDeadWitness = findNotDeadWitness(worklist, foundCycle);
    boolean isDead = Objects.isNull(notDeadWitness);
    if (isDead) {
      if (foundCycle.isTrue()) {
        for (Value deadValue : worklist.getSeenSet()) {
          recordValueIsDead(deadValue);
        }
      } else {
        assert worklist.getSeenSet().stream()
            .allMatch(deadValue -> analysisCache.get(deadValue) == ValueIsDeadResult.DEAD);
      }
    }
    return isDead;
  }

  public boolean hasDeadPhi(BasicBlock block) {
    return Iterables.any(block.getPhis(), this::isDead);
  }

  private Value findNotDeadWitness(WorkList<Value> worklist, BooleanBox foundCycle) {
    DependenceGraph dependenceGraph = new DependenceGraph();
    while (worklist.hasNext()) {
      Value value = worklist.next();

      // The first time we visit a value we have not yet added any outgoing edges to the dependence
      // graph.
      assert dependenceGraph.isLeaf(value);

      // Lookup if we have already analyzed the deadness of this value.
      ValueIsDeadResult cacheResult = analysisCache.get(value);
      if (cacheResult != null) {
        // If it is dead, then continue the search for a non-dead dependent. Otherwise this value is
        // a witness that the analysis failed.
        if (cacheResult.isDead()) {
          continue;
        } else {
          recordDependentsAreNotDead(value, dependenceGraph);
          return value;
        }
      }

      // If the value has debug users we cannot eliminate it since it represents a value in a local
      // variable that should be visible in the debugger.
      if (value.hasDebugUsers()) {
        recordValueAndDependentsAreNotDead(value, dependenceGraph);
        return value;
      }

      Set<Value> valuesRequiredToBeDead = new LinkedHashSet<>(value.uniquePhiUsers());
      for (Instruction instruction : value.uniqueUsers()) {
        DeadInstructionResult result = instruction.canBeDeadCode(appView, code);
        if (result.isNotDead()) {
          recordValueAndDependentsAreNotDead(value, dependenceGraph);
          return value;
        }
        if (result.isMaybeDead()) {
          result.getValuesRequiredToBeDead().forEach(valuesRequiredToBeDead::add);
        }
        if (instruction.hasOutValue()) {
          valuesRequiredToBeDead.add(instruction.outValue());
        }
      }

      Iterator<Value> valuesRequiredToBeDeadIterator = valuesRequiredToBeDead.iterator();
      while (valuesRequiredToBeDeadIterator.hasNext()) {
        Value valueRequiredToBeDead = valuesRequiredToBeDeadIterator.next();
        if (hasProvenThatValueIsNotDead(valueRequiredToBeDead)) {
          recordValueAndDependentsAreNotDead(value, dependenceGraph);
          return value;
        }
        if (!needsToProveThatValueIsDead(value, valueRequiredToBeDead)) {
          valuesRequiredToBeDeadIterator.remove();
        }
      }

      if (valuesRequiredToBeDead.isEmpty()) {
        // We have now proven that this value is dead.
        recordValueIsDeadAndPropagateToDependents(value, dependenceGraph);
      } else {
        // Record the current value as a dependent of each value required to be dead.
        for (Value valueRequiredToBeDead : valuesRequiredToBeDead) {
          dependenceGraph.addDependenceEdge(value, valueRequiredToBeDead);
          foundCycle.or(worklist.isSeen(valueRequiredToBeDead));
        }

        // Continue the analysis of the dependents.
        worklist.addIfNotSeen(valuesRequiredToBeDead);
      }
    }
    return null;
  }

  private boolean hasProvenThatValueIsNotDead(Value valueRequiredToBeDead) {
    return analysisCache.get(valueRequiredToBeDead) == ValueIsDeadResult.NOT_DEAD;
  }

  private boolean needsToProveThatValueIsDead(Value value, Value valueRequiredToBeDead) {
    // No need to record that the deadness of a values relies on its own removal.
    assert !hasProvenThatValueIsNotDead(valueRequiredToBeDead);
    return valueRequiredToBeDead != value && !analysisCache.containsKey(valueRequiredToBeDead);
  }

  private void recordValueIsDeadAndPropagateToDependents(
      Value value, DependenceGraph dependenceGraph) {
    WorkList<Value> worklist = WorkList.newIdentityWorkList(value);
    while (worklist.hasNext()) {
      Value current = worklist.next();
      recordValueIsDead(current);

      // This value is now proven to be dead, thus there is no need to keep track of its successors.
      dependenceGraph.unlinkSuccessors(current);

      // Continue processing of new leaves.
      for (Value dependent : dependenceGraph.removeLeaf(current)) {
        if (dependenceGraph.isLeaf(dependent)) {
          worklist.addIfNotSeen(dependent);
        }
      }
    }
  }

  private void recordValueIsDead(Value value) {
    ValueIsDeadResult existingResult = analysisCache.put(value, ValueIsDeadResult.DEAD);
    assert existingResult == null || existingResult.isDead();
  }

  private void recordValueAndDependentsAreNotDead(Value value, DependenceGraph dependenceGraph) {
    recordValueIsNotDead(value, dependenceGraph);
    recordDependentsAreNotDead(value, dependenceGraph);
  }

  private void recordValueIsNotDead(Value value, DependenceGraph dependenceGraph) {
    // This value is now proven to be dead, thus there is no need to keep track of its successors.
    dependenceGraph.unlinkSuccessors(value);
    ValueIsDeadResult existingResult = analysisCache.put(value, ValueIsDeadResult.NOT_DEAD);
    assert existingResult == null || existingResult.isNotDead();
  }

  private void recordDependentsAreNotDead(Value value, DependenceGraph dependenceGraph) {
    WorkList<Value> worklist = WorkList.newIdentityWorkList(value);
    while (worklist.hasNext()) {
      Value current = worklist.next();
      for (Value dependent : dependenceGraph.removeLeaf(current)) {
        recordValueIsNotDead(dependent, dependenceGraph);
        worklist.addIfNotSeen(dependent);
      }
    }
  }

  private static class DependenceGraph {

    private final Map<Value, Set<Value>> successors = new IdentityHashMap<>();
    private final Map<Value, Set<Value>> predecessors = new IdentityHashMap<>();

    /**
     * Records that the removal of {@param value} depends on the removal of {@param
     * valueRequiredToBeDead} by adding an edge from {@param value} to {@param
     * valueRequiredToBeDead} in this graph.
     */
    void addDependenceEdge(Value value, Value valueRequiredToBeDead) {
      successors
          .computeIfAbsent(value, ignoreKey(Sets::newIdentityHashSet))
          .add(valueRequiredToBeDead);
      predecessors
          .computeIfAbsent(valueRequiredToBeDead, ignoreKey(Sets::newIdentityHashSet))
          .add(value);
    }

    Set<Value> removeLeaf(Value value) {
      assert isLeaf(value);
      Set<Value> dependents = MapUtils.removeOrDefault(predecessors, value, Collections.emptySet());
      for (Value dependent : dependents) {
        Set<Value> dependentSuccessors = successors.get(dependent);
        boolean removed = dependentSuccessors.remove(value);
        assert removed;
        if (dependentSuccessors.isEmpty()) {
          successors.remove(dependent);
        }
      }
      return dependents;
    }

    void unlinkSuccessors(Value value) {
      Set<Value> valueSuccessors =
          MapUtils.removeOrDefault(successors, value, Collections.emptySet());
      for (Value successor : valueSuccessors) {
        Set<Value> successorPredecessors = predecessors.get(successor);
        boolean removed = successorPredecessors.remove(value);
        assert removed;
        if (successorPredecessors.isEmpty()) {
          predecessors.remove(successor);
        }
      }
    }

    boolean isLeaf(Value value) {
      return !successors.containsKey(value);
    }
  }
}
