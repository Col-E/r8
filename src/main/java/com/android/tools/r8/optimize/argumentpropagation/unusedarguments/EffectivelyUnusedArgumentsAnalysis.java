// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.unusedarguments;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodParameter;
import com.android.tools.r8.optimize.argumentpropagation.utils.ParameterRemovalUtils;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.WorkList;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Analysis to find arguments that are effectively unused. The analysis first computes the
 * constraints for a given argument to be effectively unused, and then subsequently solves the
 * computed constraints.
 *
 * <p>Example: Consider the following Companion class.
 *
 * <pre>
 *   static class Companion {
 *     void foo() {
 *       this.bar()
 *     }
 *     void bar() {
 *       doStuff();
 *     }
 *   }
 * </pre>
 *
 * <p>The analysis works as follows.
 *
 * <ol>
 *   <li>When IR processing the Companion.bar() method, the unused argument analysis records that
 *       its receiver is unused.
 *   <li>When IR processing the Companion.foo() method, the effectively unused argument analysis
 *       records that the receiver of Companion.foo() is unused if the receiver of Companion.bar()
 *       is unused.
 *   <li>After IR processing all methods, the effectively unused argument analysis builds a graph
 *       where there is a directed edge p0 -> p1 if the removal of method parameter p0 depends on
 *       the removal of method parameter p1.
 *   <li>The analysis then repeatedly removes method parameters from the graph that have no outgoing
 *       edges and marks such method parameters as being effectively unused.
 * </ol>
 *
 * <p>
 */
public class EffectivelyUnusedArgumentsAnalysis {

  private final AppView<AppInfoWithLiveness> appView;

  // Maps each method parameter p to the method parameters that must be effectively unused in order
  // for the method parameter p to be effectively unused.
  private final Map<MethodParameter, Set<MethodParameter>> constraints = new ConcurrentHashMap<>();

  // Set of virtual methods that can definitely be optimized.
  //
  // We conservatively exclude virtual methods with dynamic dispatch from this set, since the
  // parameters of such methods can only be removed if the same parameter can be removed from all
  // other virtual methods with the same signature in the class hierarchy.
  private final ProgramMethodSet optimizableVirtualMethods = ProgramMethodSet.createConcurrent();

  public EffectivelyUnusedArgumentsAnalysis(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  public void initializeOptimizableVirtualMethods(Set<DexProgramClass> stronglyConnectedComponent) {
    // Group all virtual methods in this strongly connected component by their signature.
    Map<DexMethodSignature, ProgramMethodSet> methodsBySignature = new HashMap<>();
    for (DexProgramClass clazz : stronglyConnectedComponent) {
      clazz.forEachProgramVirtualMethod(
          method -> {
            ProgramMethodSet methodsWithSameSignature =
                methodsBySignature.computeIfAbsent(
                    method.getMethodSignature(), ignoreKey(ProgramMethodSet::create));
            methodsWithSameSignature.add(method);
          });
    }
    // Mark the unique method signatures as being optimizable.
    methodsBySignature.forEach(
        (signature, methodsWithSignature) -> {
          if (methodsWithSignature.size() == 1) {
            ProgramMethod method = methodsWithSignature.getFirst();
            if (ParameterRemovalUtils.canRemoveUnusedParametersFrom(appView, method)) {
              optimizableVirtualMethods.add(method);
            }
          }
        });
  }

  public void scan(ProgramMethod method, IRCode code) {
    // If this method is not subject to optimization, then don't compute effectively unused
    // constraints for the method parameters.
    if (isUnoptimizable(method)) {
      return;
    }
    Iterator<Argument> argumentIterator = code.argumentIterator();
    while (argumentIterator.hasNext()) {
      Argument argument = argumentIterator.next();
      Value argumentValue = argument.outValue();
      Set<MethodParameter> effectivelyUnusedConstraints =
          computeEffectivelyUnusedConstraints(method, argument, argumentValue);
      if (effectivelyUnusedConstraints != null && !effectivelyUnusedConstraints.isEmpty()) {
        MethodParameter methodParameter =
            new MethodParameter(method.getReference(), argument.getIndex());
        assert !constraints.containsKey(methodParameter);
        constraints.put(methodParameter, effectivelyUnusedConstraints);
      }
    }
  }

  private Set<MethodParameter> computeEffectivelyUnusedConstraints(
      ProgramMethod method, Argument argument, Value argumentValue) {
    if (method.getDefinition().isInstanceInitializer() && argumentValue.isThis()) {
      return null;
    }
    if (method.getDefinition().willBeInlinedIntoInstanceInitializer(appView.dexItemFactory())) {
      return null;
    }
    if (!ParameterRemovalUtils.canRemoveUnusedParameter(appView, method, argument.getIndex())) {
      return null;
    }
    if (!argumentValue.getType().isClassType()
        || argumentValue.hasDebugUsers()
        || argumentValue.hasPhiUsers()) {
      return null;
    }
    Set<MethodParameter> effectivelyUnusedConstraints = new HashSet<>();
    for (Instruction user : argumentValue.uniqueUsers()) {
      if (user.isInvokeMethod()) {
        InvokeMethod invoke = user.asInvokeMethod();
        ProgramMethod resolvedMethod =
            appView
                .appInfo()
                .unsafeResolveMethodDueToDexFormatLegacy(invoke.getInvokedMethod())
                .getResolvedProgramMethod();
        if (resolvedMethod == null || isUnoptimizable(resolvedMethod)) {
          return null;
        }
        int dependentArgumentIndex =
            ListUtils.uniqueIndexMatching(invoke.arguments(), value -> value == argumentValue);
        if (dependentArgumentIndex < 0
            || !ParameterRemovalUtils.canRemoveUnusedParameter(
                appView, resolvedMethod, dependentArgumentIndex)) {
          return null;
        }
        effectivelyUnusedConstraints.add(
            new MethodParameter(resolvedMethod.getReference(), dependentArgumentIndex));
      } else {
        return null;
      }
    }
    return effectivelyUnusedConstraints;
  }

  public void computeEffectivelyUnusedArguments() {
    // Build a graph where nodes are method parameters and there is an edge from method parameter p0
    // to method parameter p1 if the removal of p0 depends on the removal of p1.
    EffectivelyUnusedArgumentsGraph dependenceGraph =
        EffectivelyUnusedArgumentsGraph.create(appView, constraints);

    // Remove all unoptimizable method parameters from the graph, as well as all nodes that depend
    // on a node that is unoptimable.
    dependenceGraph.removeUnoptimizableNodes();

    // Repeatedly mark method parameters with no outgoing edges (i.e., no dependencies) as being
    // unused.
    WorkList<EffectivelyUnusedArgumentsGraphNode> worklist =
        WorkList.newIdentityWorkList(dependenceGraph.getNodes());
    while (!worklist.isEmpty()) {
      while (!worklist.isEmpty()) {
        EffectivelyUnusedArgumentsGraphNode node = worklist.removeSeen();
        assert dependenceGraph.verifyContains(node);
        node.removeUnusedSuccessors();
        if (node.getSuccessors().isEmpty()) {
          node.setUnused();
          node.getPredecessors().forEach(worklist::addIfNotSeen);
          node.cleanForRemoval();
          dependenceGraph.remove(node);
        }
      }

      // Handle mutually recursive methods. If there is a cycle p0 -> p1 -> ... -> pn -> p0 and each
      // of the method parameters p0 ... pn has a unique successor, then remove the edge pn -> p0
      // from the graph.
      dependenceGraph.removeClosedCycles(worklist::addIfNotSeen);
    }
  }

  private boolean isUnoptimizable(ProgramMethod method) {
    if (method.getDefinition().belongsToDirectPool()) {
      return !ParameterRemovalUtils.canRemoveUnusedParametersFrom(appView, method);
    }
    if (optimizableVirtualMethods.contains(method)) {
      assert ParameterRemovalUtils.canRemoveUnusedParametersFrom(appView, method);
      return false;
    }
    return true;
  }

  public void onMethodPruned(ProgramMethod method) {
    onMethodCodePruned(method);
  }

  public void onMethodCodePruned(ProgramMethod method) {
    for (int argumentIndex = 0;
        argumentIndex < method.getDefinition().getNumberOfArguments();
        argumentIndex++) {
      constraints.remove(new MethodParameter(method.getReference(), argumentIndex));
    }
  }
}
