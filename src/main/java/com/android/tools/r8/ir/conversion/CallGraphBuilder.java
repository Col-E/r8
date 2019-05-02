// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense.GraphLenseLookupResult;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.conversion.CallGraph.Node;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CallGraphBuilder {

  private final AppView<AppInfoWithLiveness> appView;
  private final Map<DexMethod, Node> nodes = new IdentityHashMap<>();

  CallGraphBuilder(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  public CallGraph build(Timing timing) {
    for (DexProgramClass clazz : appView.appInfo().classesWithDeterministicOrder()) {
      processClass(clazz);
    }

    assert verifyAllMethodsWithCodeExists();

    timing.begin("Cycle elimination");
    CycleEliminator cycleEliminator = new CycleEliminator(nodes.values(), appView.options());
    cycleEliminator.breakCycles();
    timing.end();
    assert cycleEliminator.breakCycles() == 0; // This time the cycles should be gone.

    return new CallGraph(appView, Sets.newHashSet(nodes.values()));
  }

  private void processClass(DexProgramClass clazz) {
    for (DexEncodedMethod method : clazz.allMethodsSorted()) {
      processMethod(method);
    }
  }

  private void processMethod(DexEncodedMethod method) {
    if (method.hasCode()) {
      method.registerCodeReferences(new InvokeExtractor(getOrCreateNode(method)));
    }
  }

  private Node getOrCreateNode(DexEncodedMethod method) {
    return nodes.computeIfAbsent(method.method, ignore -> new Node(method));
  }

  private boolean verifyAllMethodsWithCodeExists() {
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      for (DexEncodedMethod method : clazz.methods()) {
        assert !method.hasCode() || nodes.get(method.method) != null;
      }
    }
    return true;
  }

  private class InvokeExtractor extends UseRegistry {

    private final Node caller;

    InvokeExtractor(Node caller) {
      super(appView.dexItemFactory());
      this.caller = caller;
    }

    private void addClassInitializerTarget(DexClass clazz) {
      assert clazz != null;
      if (clazz.hasClassInitializer() && clazz.isProgramClass()) {
        addTarget(clazz.getClassInitializer());
      }
    }

    private void addClassInitializerTarget(DexType type) {
      assert type.isClassType();
      DexClass clazz = appView.definitionFor(type);
      if (clazz != null) {
        addClassInitializerTarget(clazz);
      }
    }

    private void addTarget(DexEncodedMethod callee) {
      if (!callee.accessFlags.isAbstract()) {
        getOrCreateNode(callee).addCaller(caller);
      }
    }

    private void addPossibleTarget(DexEncodedMethod possibleTarget) {
      DexClass possibleTargetClass = appView.definitionFor(possibleTarget.method.holder);
      if (possibleTargetClass != null && possibleTargetClass.isProgramClass()) {
        addTarget(possibleTarget);
      }
    }

    private void addPossibleTargets(
        DexEncodedMethod definition, Set<DexEncodedMethod> possibleTargets) {
      for (DexEncodedMethod possibleTarget : possibleTargets) {
        if (possibleTarget != definition) {
          addPossibleTarget(possibleTarget);
        }
      }
    }

    private void processInvoke(Invoke.Type type, DexMethod method) {
      DexEncodedMethod source = caller.method;
      GraphLenseLookupResult result =
          appView.graphLense().lookupMethod(method, source.method, type);
      method = result.getMethod();
      type = result.getType();
      DexEncodedMethod definition =
          appView.appInfo().lookupSingleTarget(type, method, source.method.holder);
      if (definition != null) {
        assert !source.accessFlags.isBridge() || definition != caller.method;
        DexClass clazz = appView.definitionFor(definition.method.holder);
        assert clazz != null;
        if (clazz.isProgramClass()) {
          // For static invokes, the class could be initialized.
          if (type == Invoke.Type.STATIC) {
            addClassInitializerTarget(clazz);
          }

          addTarget(definition);
          // For virtual and interface calls add all potential targets that could be called.
          if (type == Invoke.Type.VIRTUAL || type == Invoke.Type.INTERFACE) {
            Set<DexEncodedMethod> possibleTargets;
            if (clazz.isInterface()) {
              possibleTargets = appView.appInfo().lookupInterfaceTargets(definition.method);
            } else {
              possibleTargets = appView.appInfo().lookupVirtualTargets(definition.method);
            }
            addPossibleTargets(definition, possibleTargets);
          }
        }
      }
    }

    private void processFieldAccess(DexField field) {
      // Any field access implicitly calls the class initializer.
      if (field.holder.isClassType()) {
        DexEncodedField encodedField = appView.appInfo().resolveField(field);
        if (encodedField != null && encodedField.isStatic()) {
          addClassInitializerTarget(field.holder);
        }
      }
    }

    @Override
    public boolean registerInvokeVirtual(DexMethod method) {
      processInvoke(Invoke.Type.VIRTUAL, method);
      return false;
    }

    @Override
    public boolean registerInvokeDirect(DexMethod method) {
      processInvoke(Invoke.Type.DIRECT, method);
      return false;
    }

    @Override
    public boolean registerInvokeStatic(DexMethod method) {
      processInvoke(Invoke.Type.STATIC, method);
      return false;
    }

    @Override
    public boolean registerInvokeInterface(DexMethod method) {
      processInvoke(Invoke.Type.INTERFACE, method);
      return false;
    }

    @Override
    public boolean registerInvokeSuper(DexMethod method) {
      processInvoke(Invoke.Type.SUPER, method);
      return false;
    }

    @Override
    public boolean registerInstanceFieldWrite(DexField field) {
      processFieldAccess(field);
      return false;
    }

    @Override
    public boolean registerInstanceFieldRead(DexField field) {
      processFieldAccess(field);
      return false;
    }

    @Override
    public boolean registerNewInstance(DexType type) {
      if (type.isClassType()) {
        addClassInitializerTarget(type);
      }
      return false;
    }

    @Override
    public boolean registerStaticFieldRead(DexField field) {
      processFieldAccess(field);
      return false;
    }

    @Override
    public boolean registerStaticFieldWrite(DexField field) {
      processFieldAccess(field);
      return false;
    }

    @Override
    public boolean registerTypeReference(DexType type) {
      return false;
    }
  }

  public static class CycleEliminator {

    public static final String CYCLIC_FORCE_INLINING_MESSAGE =
        "Unable to satisfy force inlining constraints due to cyclic force inlining";

    private static class CallEdge {

      private final Node caller;
      private final Node callee;

      public CallEdge(Node caller, Node callee) {
        this.caller = caller;
        this.callee = callee;
      }

      public void remove() {
        callee.removeCaller(caller);
      }
    }

    private final Collection<Node> nodes;
    private final InternalOptions options;

    // DFS stack.
    private Deque<Node> stack = new ArrayDeque<>();

    // Set of nodes on the DFS stack.
    private Set<Node> stackSet = Sets.newIdentityHashSet();

    // Set of nodes that have been visited entirely.
    private Set<Node> marked = Sets.newIdentityHashSet();

    private int numberOfCycles = 0;

    public CycleEliminator(Collection<Node> nodes, InternalOptions options) {
      this.options = options;

      // Call to reorderNodes must happen after assigning options.
      this.nodes =
          options.testing.nondeterministicCycleElimination
              ? reorderNodes(new ArrayList<>(nodes))
              : nodes;
    }

    public int breakCycles() {
      // Break cycles in this call graph by removing edges causing cycles.
      for (Node node : nodes) {
        traverse(node);
      }
      int result = numberOfCycles;
      reset();
      return result;
    }

    private void reset() {
      assert stack.isEmpty();
      assert stackSet.isEmpty();
      marked.clear();
      numberOfCycles = 0;
    }

    private void traverse(Node node) {
      if (marked.contains(node)) {
        // Already visited all nodes that can be reached from this node.
        return;
      }

      push(node);

      // Sort the callees before calling traverse recursively. This will ensure cycles are broken
      // the same way across multiple invocations of the R8 compiler.
      Node[] callees = node.getCalleesWithDeterministicOrder();

      if (options.testing.nondeterministicCycleElimination) {
        reorderNodes(Arrays.asList(callees));
      }

      for (Node callee : callees) {
        if (stackSet.contains(callee)) {
          // Found a cycle that needs to be eliminated.
          numberOfCycles++;

          if (edgeRemovalIsSafe(node, callee)) {
            // Break the cycle by removing the edge node->callee.
            callee.removeCaller(node);

            if (Log.ENABLED) {
              Log.info(
                  CallGraph.class,
                  "Removed call edge from method '%s' to '%s'",
                  node.method.toSourceString(),
                  callee.method.toSourceString());
            }
          } else {
            // The cycle has a method that is marked as force inline.
            LinkedList<Node> cycle = extractCycle(callee);

            if (Log.ENABLED) {
              Log.info(
                  CallGraph.class, "Extracted cycle to find an edge that can safely be removed");
            }

            // Break the cycle by finding an edge that can be removed without breaking force
            // inlining. If that is not possible, this call fails with a compilation error.
            CallEdge edge = findCallEdgeForRemoval(cycle);

            // The edge will be null if this cycle has already been eliminated as a result of
            // another cycle elimination.
            if (edge != null) {
              assert edgeRemovalIsSafe(edge.caller, edge.callee);

              // Break the cycle by removing the edge caller->callee.
              edge.remove();

              if (Log.ENABLED) {
                Log.info(
                    CallGraph.class,
                    "Removed call edge from force inlined method '%s' to '%s' to ensure that "
                        + "force inlining will succeed",
                    node.method.toSourceString(),
                    callee.method.toSourceString());
              }
            }

            // Recover the stack.
            recoverStack(cycle);
          }
        } else {
          traverse(callee);
        }
      }
      pop(node);
      marked.add(node);
    }

    private void push(Node node) {
      stack.push(node);
      boolean changed = stackSet.add(node);
      assert changed;
    }

    private void pop(Node node) {
      Node popped = stack.pop();
      assert popped == node;
      boolean changed = stackSet.remove(node);
      assert changed;
    }

    private LinkedList<Node> extractCycle(Node entry) {
      LinkedList<Node> cycle = new LinkedList<>();
      do {
        assert !stack.isEmpty();
        cycle.add(stack.pop());
      } while (cycle.getLast() != entry);
      return cycle;
    }

    private CallEdge findCallEdgeForRemoval(LinkedList<Node> extractedCycle) {
      Node callee = extractedCycle.getLast();
      for (Node caller : extractedCycle) {
        if (!caller.hasCallee(callee)) {
          // No need to break any edges since this cycle has already been broken previously.
          assert !callee.hasCaller(caller);
          return null;
        }
        if (edgeRemovalIsSafe(caller, callee)) {
          return new CallEdge(caller, callee);
        }
        callee = caller;
      }
      throw new CompilationError(CYCLIC_FORCE_INLINING_MESSAGE);
    }

    private static boolean edgeRemovalIsSafe(Node caller, Node callee) {
      // All call edges where the callee is a method that should be force inlined must be kept,
      // to guarantee that the IR converter will process the callee before the caller.
      return !callee.method.getOptimizationInfo().forceInline();
    }

    private void recoverStack(LinkedList<Node> extractedCycle) {
      Iterator<Node> descendingIt = extractedCycle.descendingIterator();
      while (descendingIt.hasNext()) {
        stack.push(descendingIt.next());
      }
    }

    private Collection<Node> reorderNodes(List<Node> nodes) {
      assert options.testing.nondeterministicCycleElimination;
      if (!InternalOptions.DETERMINISTIC_DEBUGGING) {
        Collections.shuffle(nodes);
      }
      return nodes;
    }
  }
}
