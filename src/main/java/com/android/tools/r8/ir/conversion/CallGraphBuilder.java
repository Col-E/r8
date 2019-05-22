// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppInfo.ResolutionResult;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense.GraphLenseLookupResult;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.conversion.CallGraph.Node;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class CallGraphBuilder {

  private final AppView<AppInfoWithLiveness> appView;
  private final Map<DexMethod, Node> nodes = new IdentityHashMap<>();
  private final Map<DexMethod, Set<DexEncodedMethod>> possibleTargetsCache =
      new ConcurrentHashMap<>();

  CallGraphBuilder(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  public CallGraph build(ExecutorService executorService, Timing timing) throws ExecutionException {
    List<Future<?>> futures = new ArrayList<>();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (clazz.hasMethods()) {
        futures.add(
            executorService.submit(
                () -> {
                  processClass(clazz);
                  return null; // we want a Callable not a Runnable to be able to throw
                }));
      }
    }
    ThreadUtils.awaitFutures(futures);

    assert verifyAllMethodsWithCodeExists();

    timing.begin("Cycle elimination");
    // Sort the nodes for deterministic cycle elimination.
    Set<Node> nodesWithDeterministicOrder = Sets.newTreeSet(nodes.values());
    CycleEliminator cycleEliminator =
        new CycleEliminator(nodesWithDeterministicOrder, appView.options());
    cycleEliminator.breakCycles();
    timing.end();
    assert cycleEliminator.breakCycles() == 0; // This time the cycles should be gone.

    return new CallGraph(nodesWithDeterministicOrder);
  }

  private void processClass(DexProgramClass clazz) {
    clazz.forEachMethod(this::processMethod);
  }

  private void processMethod(DexEncodedMethod method) {
    if (method.hasCode()) {
      method.registerCodeReferences(new InvokeExtractor(getOrCreateNode(method)));
    }
  }

  private Node getOrCreateNode(DexEncodedMethod method) {
    synchronized (nodes) {
      return nodes.computeIfAbsent(method.method, ignore -> new Node(method));
    }
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
        assert callee.isProgramMethod(appView);
        getOrCreateNode(callee).addCallerConcurrently(caller);
      }
    }

    private void processInvoke(Type originalType, DexMethod originalMethod) {
      DexEncodedMethod source = caller.method;
      DexMethod context = source.method;
      GraphLenseLookupResult result =
          appView.graphLense().lookupMethod(originalMethod, context, originalType);
      DexMethod method = result.getMethod();
      Type type = result.getType();
      if (type == Type.INTERFACE || type == Type.VIRTUAL) {
        // For virtual and interface calls add all potential targets that could be called.
        ResolutionResult resolutionResult = appView.appInfo().resolveMethod(method.holder, method);
        resolutionResult.forEachTarget(target -> processInvokeWithDynamicDispatch(type, target));
      } else {
        DexEncodedMethod singleTarget =
            appView.appInfo().lookupSingleTarget(type, method, context.holder);
        if (singleTarget != null) {
          assert !source.accessFlags.isBridge() || singleTarget != caller.method;
          DexClass clazz = appView.definitionFor(singleTarget.method.holder);
          assert clazz != null;
          if (clazz.isProgramClass()) {
            // For static invokes, the class could be initialized.
            if (type == Type.STATIC) {
              addClassInitializerTarget(clazz);
            }
            addTarget(singleTarget);
          }
        }
      }
    }

    private void processInvokeWithDynamicDispatch(Type type, DexEncodedMethod encodedTarget) {
      DexMethod target = encodedTarget.method;
      DexClass clazz = appView.definitionFor(target.holder);
      if (clazz == null) {
        assert false : "Unable to lookup holder of `" + target.toSourceString() + "`";
        return;
      }

      if (!appView.options().testing.addCallEdgesForLibraryInvokes) {
        if (clazz.isLibraryClass()) {
          // Likely to have many possible targets.
          return;
        }
      }

      Set<DexEncodedMethod> possibleTargets =
          possibleTargetsCache.computeIfAbsent(
              target,
              method ->
                  type == Type.INTERFACE
                      ? appView.appInfo().lookupInterfaceTargets(method)
                      : appView.appInfo().lookupVirtualTargets(method));
      if (possibleTargets != null) {
        for (DexEncodedMethod possibleTarget : possibleTargets) {
          if (possibleTarget.isProgramMethod(appView)) {
            addTarget(possibleTarget);
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
      processInvoke(Type.VIRTUAL, method);
      return false;
    }

    @Override
    public boolean registerInvokeDirect(DexMethod method) {
      processInvoke(Type.DIRECT, method);
      return false;
    }

    @Override
    public boolean registerInvokeStatic(DexMethod method) {
      processInvoke(Type.STATIC, method);
      return false;
    }

    @Override
    public boolean registerInvokeInterface(DexMethod method) {
      processInvoke(Type.INTERFACE, method);
      return false;
    }

    @Override
    public boolean registerInvokeSuper(DexMethod method) {
      processInvoke(Type.SUPER, method);
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

    @Override
    public void registerCallSite(DexCallSite callSite) {
      registerMethodHandle(
          callSite.bootstrapMethod, MethodHandleUse.NOT_ARGUMENT_TO_LAMBDA_METAFACTORY);
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

    private int currentDepth = 0;
    private int maxDepth = 0;
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
        assert currentDepth == 0;
        traverse(node);
      }
      int result = numberOfCycles;
      if (Log.ENABLED) {
        Log.info(getClass(), "# call graph cycles broken: %s", numberOfCycles);
        Log.info(getClass(), "# max call graph depth: %s", maxDepth);
      }
      reset();
      return result;
    }

    private void reset() {
      assert currentDepth == 0;
      assert stack.isEmpty();
      assert stackSet.isEmpty();
      marked.clear();
      maxDepth = 0;
      numberOfCycles = 0;
    }

    private void traverse(Node node) {
      if (Log.ENABLED) {
        if (currentDepth > maxDepth) {
          maxDepth = currentDepth;
        }
      }

      if (marked.contains(node)) {
        // Already visited all nodes that can be reached from this node.
        return;
      }

      push(node);

      // The callees must be sorted before calling traverse recursively. This ensures that cycles
      // are broken the same way across multiple compilations.
      Collection<Node> callees = node.getCalleesWithDeterministicOrder();

      if (options.testing.nondeterministicCycleElimination) {
        callees = reorderNodes(new ArrayList<>(callees));
      }

      Iterator<Node> calleeIterator = callees.iterator();
      while (calleeIterator.hasNext()) {
        Node callee = calleeIterator.next();

        // If we've exceeded the depth threshold, then treat it as if we have found a cycle. This
        // ensures that we won't run into stack overflows when the call graph contains large call
        // chains. This should have a negligible impact on code size as long as the threshold is
        // large enough.
        boolean foundCycle = stackSet.contains(callee);
        boolean thresholdExceeded =
            currentDepth >= options.callGraphCycleEliminatorMaxDepthThreshold
                && edgeRemovalIsSafe(node, callee);
        if (foundCycle || thresholdExceeded) {
          // Found a cycle that needs to be eliminated.
          numberOfCycles++;

          if (edgeRemovalIsSafe(node, callee)) {
            // Break the cycle by removing the edge node->callee.
            if (options.testing.nondeterministicCycleElimination) {
              callee.removeCaller(node);
            } else {
              // Need to remove `callee` from `node.callees` using the iterator to prevent a
              // ConcurrentModificationException. This is not needed when nondeterministic cycle
              // elimination is enabled, because we iterate a copy of `node.callees` in that case.
              calleeIterator.remove();
              callee.getCallersWithDeterministicOrder().remove(node);
            }

            if (Log.ENABLED) {
              Log.info(
                  CallGraph.class,
                  "Removed call edge from method '%s' to '%s'",
                  node.method.toSourceString(),
                  callee.method.toSourceString());
            }
          } else {
            assert foundCycle;

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
          currentDepth++;
          traverse(callee);
          currentDepth--;
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
