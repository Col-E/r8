// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.GraphLense.GraphLenseLookupResult;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.utils.IROrdering;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.ThrowingBiConsumer;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Call graph representation.
 * <p>
 * Each node in the graph contain the methods called and the calling methods. For virtual and
 * interface calls all potential calls from subtypes are recorded.
 * <p>
 * Only methods in the program - not library methods - are represented.
 * <p>
 * The directional edges are represented as sets of nodes in each node (called methods and callees).
 * <p>
 * A call from method <code>a</code> to method <code>b</code> is only present once no matter how
 * many calls of <code>a</code> there are in <code>a</code>.
 * <p>
 * Recursive calls are not present.
 */
public class CallGraph extends CallSiteInformation {

  private CallGraph(InternalOptions options) {
    this.shuffle = options.testing.irOrdering;
  }

  public static class Node {

    public final DexEncodedMethod method;
    private int invokeCount = 0;
    private boolean isSelfRecursive = false;

    // Outgoing calls from this method.
    private final Set<Node> callees = new LinkedHashSet<>();

    // Incoming calls to this method.
    private final Set<Node> callers = new LinkedHashSet<>();

    public Node(DexEncodedMethod method) {
      this.method = method;
    }

    public boolean isBridge() {
      return method.accessFlags.isBridge();
    }

    public void addCallee(Node method) {
      callees.add(method);
      method.callers.add(this);
    }

    public boolean hasCallee(Node method) {
      return callees.contains(method);
    }

    boolean isSelfRecursive() {
      return isSelfRecursive;
    }

    public boolean isLeaf() {
      return callees.isEmpty();
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append("MethodNode for: ");
      builder.append(method.toSourceString());
      builder.append(" (");
      builder.append(callees.size());
      builder.append(" callees, ");
      builder.append(callers.size());
      builder.append(" callers");
      if (isBridge()) {
        builder.append(", bridge");
      }
      if (isSelfRecursive()) {
        builder.append(", recursive");
      }
      builder.append(", invoke count ").append(invokeCount);
      builder.append(").\n");
      if (callees.size() > 0) {
        builder.append("Callees:\n");
        for (Node call : callees) {
          builder.append("  ");
          builder.append(call.method.toSourceString());
          builder.append("\n");
        }
      }
      if (callers.size() > 0) {
        builder.append("Callers:\n");
        for (Node caller : callers) {
          builder.append("  ");
          builder.append(caller.method.toSourceString());
          builder.append("\n");
        }
      }
      return builder.toString();
    }
  }

  private final Map<DexEncodedMethod, Node> nodes = new LinkedHashMap<>();
  private final IROrdering shuffle;

  private final Set<DexEncodedMethod> singleCallSite = Sets.newIdentityHashSet();
  private final Set<DexEncodedMethod> doubleCallSite = Sets.newIdentityHashSet();

  public static CallGraph build(
      DexApplication application,
      AppInfoWithLiveness appInfo,
      GraphLense graphLense,
      InternalOptions options,
      Timing timing) {
    CallGraph graph = new CallGraph(options);
    DexClass[] classes = application.classes().toArray(new DexClass[application.classes().size()]);
    Arrays.sort(classes, (DexClass a, DexClass b) -> a.type.slowCompareTo(b.type));
    for (DexClass clazz : classes) {
      for (DexEncodedMethod method : clazz.allMethodsSorted()) {
        Node node = graph.ensureMethodNode(method);
        InvokeExtractor extractor = new InvokeExtractor(appInfo, graphLense, node, graph);
        method.registerCodeReferences(extractor);
      }
    }
    assert allMethodsExists(application, graph);

    timing.begin("Cycle elimination");
    CycleEliminator cycleEliminator = new CycleEliminator(graph.nodes.values(), options);
    cycleEliminator.breakCycles();
    timing.end();
    assert cycleEliminator.breakCycles() == 0; // This time the cycles should be gone.

    graph.fillCallSiteSets(appInfo);
    return graph;
  }

  /**
   * Check if the <code>method</code> is guaranteed to only have a single call site.
   * <p>
   * For pinned methods (methods kept through Proguard keep rules) this will always answer
   * <code>false</code>.
   */
  @Override
  public boolean hasSingleCallSite(DexEncodedMethod method) {
    return singleCallSite.contains(method);
  }

  @Override
  public boolean hasDoubleCallSite(DexEncodedMethod method) {
    return doubleCallSite.contains(method);
  }

  private void fillCallSiteSets(AppInfoWithLiveness appInfo) {
    assert singleCallSite.isEmpty();
    for (Node value : nodes.values()) {
      // For non-pinned methods we know the exact number of call sites.
      if (!appInfo.isPinned(value.method.method)) {
        if (value.invokeCount == 1) {
          singleCallSite.add(value.method);
        } else if (value.invokeCount == 2) {
          doubleCallSite.add(value.method);
        }
      }
    }
  }

  private static boolean allMethodsExists(DexApplication application, CallGraph graph) {
    for (DexProgramClass clazz : application.classes()) {
      clazz.forEachMethod(method -> {
        assert graph.nodes.get(method) != null;
      });
    }
    return true;
  }

  /**
   * Extract the next set of leaves (nodes with an call (outgoing) degree of 0) if any.
   *
   * <p>All nodes in the graph are extracted if called repeatedly until null is returned. Please
   * note that there are no cycles in this graph (see {@link CycleEliminator#breakCycles}).
   *
   * <p>
   */
  private Collection<DexEncodedMethod> extractLeaves() {
    if (isEmpty()) {
      return Collections.emptySet();
    }
    // First identify all leaves before removing them from the graph.
    List<Node> leaves = nodes.values().stream().filter(Node::isLeaf).collect(Collectors.toList());
    leaves.forEach(leaf -> {
      leaf.callers.forEach(caller -> caller.callees.remove(leaf));
      nodes.remove(leaf.method);
    });
    Set<DexEncodedMethod> methods =
        leaves.stream().map(x -> x.method).collect(Collectors.toCollection(LinkedHashSet::new));
    // TODO(b/116282409): Resolve why shuffling makes art.none.r8.Art800_smaliTest flaky.
    return methods;
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
      Node[] callees = node.callees.toArray(new Node[node.callees.size()]);
      Arrays.sort(callees, (Node a, Node b) -> a.method.method.slowCompareTo(b.method.method));
      if (options.testing.nondeterministicCycleElimination) {
        reorderNodes(Arrays.asList(callees));
      }

      for (Node callee : callees) {
        if (stackSet.contains(callee)) {
          // Found a cycle that needs to be eliminated.
          numberOfCycles++;

          if (edgeRemovalIsSafe(node, callee)) {
            // Break the cycle by removing the edge node->callee.
            callee.callers.remove(node);
            node.callees.remove(callee);

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
              edge.caller.callees.remove(edge.callee);
              edge.callee.callers.remove(edge.caller);

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
        if (!caller.callees.contains(callee)) {
          // No need to break any edges since this cycle has already been broken previously.
          assert !callee.callers.contains(caller);
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

  synchronized private Node ensureMethodNode(DexEncodedMethod method) {
    return nodes.computeIfAbsent(method, k -> new Node(method));
  }

  synchronized private void addCall(Node caller, Node callee) {
    assert caller != null;
    assert callee != null;
    if (caller != callee) {
      caller.addCallee(callee);
    } else {
      caller.isSelfRecursive = true;
    }
    callee.invokeCount++;
  }

  public boolean isEmpty() {
    return nodes.size() == 0;
  }

  /**
   * Applies the given method to all leaf nodes of the graph.
   * <p>
   * As second parameter, a predicate that can be used to decide whether another method is
   * processed at the same time is passed. This can be used to avoid races in concurrent processing.
   */
  public <E extends Exception> void forEachMethod(
      ThrowingBiConsumer<DexEncodedMethod, Predicate<DexEncodedMethod>, E> consumer,
      ExecutorService executorService)
      throws ExecutionException {
    while (!isEmpty()) {
      Collection<DexEncodedMethod> methods = extractLeaves();
      assert methods.size() > 0;
      List<Future<?>> futures = new ArrayList<>();
      for (DexEncodedMethod method : methods) {
        futures.add(executorService.submit(() -> {
          consumer.accept(method, methods::contains);
          return null; // we want a Callable not a Runnable to be able to throw
        }));
      }
      ThreadUtils.awaitFutures(futures);
    }
  }

  public void dump() {
    nodes.forEach((m, n) -> System.out.println(n + "\n"));
  }

  private static class InvokeExtractor extends UseRegistry {

    AppInfoWithLiveness appInfo;
    GraphLense graphLense;
    Node caller;
    CallGraph graph;

    InvokeExtractor(AppInfoWithLiveness appInfo, GraphLense graphLense, Node caller,
        CallGraph graph) {
      super(appInfo.dexItemFactory);
      this.appInfo = appInfo;
      this.graphLense = graphLense;
      this.caller = caller;
      this.graph = graph;
    }

    private void addClassInitializerTarget(DexClass clazz) {
      assert clazz != null;
      if (clazz.hasClassInitializer() && !clazz.isLibraryClass()) {
        DexEncodedMethod possibleTarget = clazz.getClassInitializer();
        addTarget(possibleTarget);
      }
    }

    private void addClassInitializerTarget(DexType type) {
      if (type.isArrayType()) {
        type = type.toBaseType(appInfo.dexItemFactory);
      }
      DexClass clazz = appInfo.definitionFor(type);
      if (clazz != null) {
        addClassInitializerTarget(clazz);
      }
    }

    private void addTarget(DexEncodedMethod target) {
      Node callee = graph.ensureMethodNode(target);
      graph.addCall(caller, callee);
    }

    private void addPossibleTarget(DexEncodedMethod possibleTarget) {
      DexClass possibleTargetClass =
          appInfo.definitionFor(possibleTarget.method.getHolder());
      if (possibleTargetClass != null && !possibleTargetClass.isLibraryClass()) {
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

    private void processInvoke(Type type, DexMethod method) {
      DexEncodedMethod source = caller.method;
      GraphLenseLookupResult result = graphLense.lookupMethod(method, source, type);
      method = result.getMethod();
      type = result.getType();
      DexEncodedMethod definition = appInfo.lookup(type, method, source.method.holder);
      if (definition != null) {
        assert !source.accessFlags.isBridge() || definition != caller.method;
        DexClass definitionHolder = appInfo.definitionFor(definition.method.getHolder());
        assert definitionHolder != null;
        if (!definitionHolder.isLibraryClass()) {
          addClassInitializerTarget(definitionHolder);
          addTarget(definition);
          // For virtual and interface calls add all potential targets that could be called.
          if (type == Type.VIRTUAL || type == Type.INTERFACE) {
            Set<DexEncodedMethod> possibleTargets;
            if (definitionHolder.isInterface()) {
              possibleTargets = appInfo.lookupInterfaceTargets(definition.method);
            } else {
              possibleTargets = appInfo.lookupVirtualTargets(definition.method);
            }
            addPossibleTargets(definition, possibleTargets);
          }
        }
      }
    }

    private void processFieldAccess(DexField field) {
      // Any field access implicitly calls the class initializer.
      addClassInitializerTarget(field.getHolder());
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
      addClassInitializerTarget(type);
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
}
