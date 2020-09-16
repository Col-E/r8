// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessInfo;
import com.android.tools.r8.graph.FieldAccessInfoCollection;
import com.android.tools.r8.graph.GraphLens.MethodLookupResult;
import com.android.tools.r8.graph.LookupResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ResolutionResult;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.conversion.CallGraph.Node;
import com.android.tools.r8.ir.conversion.CallGraphBuilderBase.CycleEliminator.CycleEliminationResult;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Predicate;

abstract class CallGraphBuilderBase {

  final AppView<AppInfoWithLiveness> appView;
  private final FieldAccessInfoCollection<?> fieldAccessInfoCollection;
  final Map<DexMethod, Node> nodes = new IdentityHashMap<>();
  private final Map<DexMethod, ProgramMethodSet> possibleProgramTargetsCache =
      new ConcurrentHashMap<>();

  CallGraphBuilderBase(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.fieldAccessInfoCollection = appView.appInfo().getFieldAccessInfoCollection();
  }

  public CallGraph build(ExecutorService executorService, Timing timing) throws ExecutionException {
    timing.begin("Build IR processing order constraints");
    timing.begin("Build call graph");
    populateGraph(executorService);
    assert verifyNoRedundantFieldReadEdges();
    timing.end();
    assert verifyAllMethodsWithCodeExists();

    appView.withGeneratedMessageLiteBuilderShrinker(
        shrinker -> shrinker.preprocessCallGraphBeforeCycleElimination(nodes));

    timing.begin("Cycle elimination");
    // Sort the nodes for deterministic cycle elimination.
    Set<Node> nodesWithDeterministicOrder = Sets.newTreeSet(nodes.values());
    CycleEliminator cycleEliminator = new CycleEliminator();
    CycleEliminationResult cycleEliminationResult =
        cycleEliminator.breakCycles(nodesWithDeterministicOrder);
    timing.end();
    timing.end();
    assert cycleEliminator.breakCycles(nodesWithDeterministicOrder).numberOfRemovedCallEdges()
        == 0; // The cycles should be gone.

    return new CallGraph(nodesWithDeterministicOrder, cycleEliminationResult);
  }

  abstract void populateGraph(ExecutorService executorService) throws ExecutionException;

  /** Verify that there are no field read edges in the graph if there is also a call graph edge. */
  private boolean verifyNoRedundantFieldReadEdges() {
    for (Node writer : nodes.values()) {
      for (Node reader : writer.getReadersWithDeterministicOrder()) {
        assert !writer.hasCaller(reader);
      }
    }
    return true;
  }

  Node getOrCreateNode(ProgramMethod method) {
    synchronized (nodes) {
      return nodes.computeIfAbsent(method.getReference(), ignore -> new Node(method));
    }
  }

  abstract boolean verifyAllMethodsWithCodeExists();

  class InvokeExtractor extends UseRegistry {

    private final Node currentMethod;
    private final Predicate<ProgramMethod> targetTester;

    InvokeExtractor(Node currentMethod, Predicate<ProgramMethod> targetTester) {
      super(appView.dexItemFactory());
      this.currentMethod = currentMethod;
      this.targetTester = targetTester;
    }

    private void addClassInitializerTarget(DexProgramClass clazz) {
      assert clazz != null;
      if (clazz.hasClassInitializer()) {
        addCallEdge(clazz.getProgramClassInitializer(), false);
      }
    }

    private void addClassInitializerTarget(DexType type) {
      assert type.isClassType();
      DexProgramClass clazz = asProgramClassOrNull(appView.definitionFor(type));
      if (clazz != null) {
        addClassInitializerTarget(clazz);
      }
    }

    private void addCallEdge(ProgramMethod callee, boolean likelySpuriousCallEdge) {
      if (!targetTester.test(callee)) {
        return;
      }
      if (callee.getDefinition().isAbstract()) {
        // Not a valid target.
        return;
      }
      if (callee.getDefinition().isNative()) {
        // We don't care about calls to native methods.
        return;
      }
      if (appView.appInfo().isPinned(callee.getReference())) {
        // Since the callee is kept, we cannot inline it into the caller, and we also cannot collect
        // any optimization info for the method. Therefore, we drop the call edge to reduce the
        // total number of call graph edges, which should lead to fewer call graph cycles.
        return;
      }
      getOrCreateNode(callee).addCallerConcurrently(currentMethod, likelySpuriousCallEdge);
    }

    private void addFieldReadEdge(DexEncodedMethod writer) {
      addFieldReadEdge(writer.asProgramMethod(appView));
    }

    private void addFieldReadEdge(ProgramMethod writer) {
      assert !writer.getDefinition().isAbstract();
      if (!targetTester.test(writer)) {
        return;
      }
      getOrCreateNode(writer).addReaderConcurrently(currentMethod);
    }

    private void processInvoke(Invoke.Type originalType, DexMethod originalMethod) {
      ProgramMethod context = currentMethod.getProgramMethod();
      MethodLookupResult result =
          appView.graphLens().lookupMethod(originalMethod, context.getReference(), originalType);
      DexMethod method = result.getReference();
      Invoke.Type type = result.getType();
      if (type == Invoke.Type.INTERFACE || type == Invoke.Type.VIRTUAL) {
        // For virtual and interface calls add all potential targets that could be called.
        ResolutionResult resolutionResult =
            appView.appInfo().resolveMethod(method, type == Invoke.Type.INTERFACE);
        DexEncodedMethod target = resolutionResult.getSingleTarget();
        if (target != null) {
          processInvokeWithDynamicDispatch(type, target, context);
        }
      } else {
        ProgramMethod singleTarget =
            appView.appInfo().lookupSingleProgramTarget(type, method, context, appView);
        if (singleTarget != null) {
          assert !context.getDefinition().isBridge()
              || singleTarget.getDefinition() != context.getDefinition();
          // For static invokes, the class could be initialized.
          if (type == Invoke.Type.STATIC) {
            addClassInitializerTarget(singleTarget.getHolder());
          }
          addCallEdge(singleTarget, false);
        }
      }
    }

    private void processInvokeWithDynamicDispatch(
        Invoke.Type type, DexEncodedMethod encodedTarget, ProgramMethod context) {
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

      boolean isInterface = type == Invoke.Type.INTERFACE;
      ProgramMethodSet possibleProgramTargets =
          possibleProgramTargetsCache.computeIfAbsent(
              target,
              method -> {
                ResolutionResult resolution = appView.appInfo().resolveMethod(method, isInterface);
                if (resolution.isVirtualTarget()) {
                  LookupResult lookupResult =
                      resolution.lookupVirtualDispatchTargets(
                          context.getHolder(), appView.appInfo());
                  if (lookupResult.isLookupResultSuccess()) {
                    ProgramMethodSet targets = ProgramMethodSet.create();
                    lookupResult
                        .asLookupResultSuccess()
                        .forEach(
                            methodTarget -> {
                              if (methodTarget.isProgramMethod()) {
                                targets.add(methodTarget.asProgramMethod());
                              }
                            },
                            lambdaTarget -> {
                              // The call target will ultimately be the implementation method.
                              DexClassAndMethod implementationMethod =
                                  lambdaTarget.getImplementationMethod();
                              if (implementationMethod.isProgramMethod()) {
                                targets.add(implementationMethod.asProgramMethod());
                              }
                            });
                    return targets;
                  }
                }
                return null;
              });
      if (possibleProgramTargets != null) {
        boolean likelySpuriousCallEdge =
            possibleProgramTargets.size()
                >= appView.options().callGraphLikelySpuriousCallEdgeThreshold;
        for (ProgramMethod possibleTarget : possibleProgramTargets) {
          addCallEdge(possibleTarget, likelySpuriousCallEdge);
        }
      }
    }

    private void processFieldRead(DexField field) {
      if (!field.holder.isClassType()) {
        return;
      }

      DexEncodedField encodedField = appView.appInfo().resolveField(field).getResolvedField();
      if (encodedField == null || appView.appInfo().isPinned(encodedField.field)) {
        return;
      }

      DexProgramClass clazz = asProgramClassOrNull(appView.definitionFor(encodedField.holder()));
      if (clazz == null) {
        return;
      }

      // Each static field access implicitly triggers the class initializer.
      if (encodedField.isStatic()) {
        addClassInitializerTarget(clazz);
      }

      FieldAccessInfo fieldAccessInfo = fieldAccessInfoCollection.get(encodedField.field);
      if (fieldAccessInfo != null && fieldAccessInfo.hasKnownWriteContexts()) {
        if (fieldAccessInfo.getNumberOfWriteContexts() == 1) {
          fieldAccessInfo.forEachWriteContext(this::addFieldReadEdge);
        }
      }
    }

    private void processFieldWrite(DexField field) {
      if (field.holder.isClassType()) {
        DexEncodedField encodedField = appView.appInfo().resolveField(field).getResolvedField();
        if (encodedField != null && encodedField.isStatic()) {
          // Each static field access implicitly triggers the class initializer.
          addClassInitializerTarget(field.holder);
        }
      }
    }

    private void processInitClass(DexType type) {
      DexProgramClass clazz = asProgramClassOrNull(appView.definitionFor(type));
      if (clazz == null) {
        assert false;
        return;
      }
      addClassInitializerTarget(clazz);
    }

    @Override
    public void registerInitClass(DexType clazz) {
      processInitClass(clazz);
    }

    @Override
    public void registerInvokeVirtual(DexMethod method) {
      processInvoke(Invoke.Type.VIRTUAL, method);
    }

    @Override
    public void registerInvokeDirect(DexMethod method) {
      processInvoke(Invoke.Type.DIRECT, method);
    }

    @Override
    public void registerInvokeStatic(DexMethod method) {
      processInvoke(Invoke.Type.STATIC, method);
    }

    @Override
    public void registerInvokeInterface(DexMethod method) {
      processInvoke(Invoke.Type.INTERFACE, method);
    }

    @Override
    public void registerInvokeSuper(DexMethod method) {
      processInvoke(Invoke.Type.SUPER, method);
    }

    @Override
    public void registerInstanceFieldRead(DexField field) {
      processFieldRead(field);
    }

    @Override
    public void registerInstanceFieldWrite(DexField field) {
      processFieldWrite(field);
    }

    @Override
    public void registerNewInstance(DexType type) {
      if (type.isClassType()) {
        addClassInitializerTarget(type);
      }
    }

    @Override
    public void registerStaticFieldRead(DexField field) {
      processFieldRead(field);
    }

    @Override
    public void registerStaticFieldWrite(DexField field) {
      processFieldWrite(field);
    }

    @Override
    public void registerTypeReference(DexType type) {}

    @Override
    public void registerInstanceOf(DexType type) {}

    @Override
    public void registerCallSite(DexCallSite callSite) {
      registerMethodHandle(
          callSite.bootstrapMethod, MethodHandleUse.NOT_ARGUMENT_TO_LAMBDA_METAFACTORY);
    }
  }

  static class CycleEliminator {

    static final String CYCLIC_FORCE_INLINING_MESSAGE =
        "Unable to satisfy force inlining constraints due to cyclic force inlining";

    private static class CallEdge {

      private final Node caller;
      private final Node callee;

      CallEdge(Node caller, Node callee) {
        this.caller = caller;
        this.callee = callee;
      }
    }

    static class StackEntryInfo {

      final int index;
      final Node predecessor;

      boolean processed;

      StackEntryInfo(int index, Node predecessor) {
        this.index = index;
        this.predecessor = predecessor;
      }
    }

    static class CycleEliminationResult {

      private Map<DexEncodedMethod, ProgramMethodSet> removedCallEdges;

      CycleEliminationResult(Map<DexEncodedMethod, ProgramMethodSet> removedCallEdges) {
        this.removedCallEdges = removedCallEdges;
      }

      void forEachRemovedCaller(ProgramMethod callee, Consumer<ProgramMethod> fn) {
        removedCallEdges.getOrDefault(callee.getDefinition(), ProgramMethodSet.empty()).forEach(fn);
      }

      int numberOfRemovedCallEdges() {
        int numberOfRemovedCallEdges = 0;
        for (ProgramMethodSet nodes : removedCallEdges.values()) {
          numberOfRemovedCallEdges += nodes.size();
        }
        return numberOfRemovedCallEdges;
      }
    }

    // DFS stack.
    private Deque<Node> stack = new ArrayDeque<>();

    // Nodes on the DFS stack.
    private Map<Node, StackEntryInfo> stackEntryInfo = new IdentityHashMap<>();

    // Subset of the DFS stack, where the nodes on the stack satisfy that the edge from the
    // predecessor to the node itself is a field read edge.
    //
    // This stack is used to efficiently compute if there is a field read edge inside a cycle when
    // a cycle is found.
    private Deque<Node> writerStack = new ArrayDeque<>();

    // Set of nodes that have been visited entirely.
    private Set<Node> marked = Sets.newIdentityHashSet();

    // Call edges that should be removed when the caller has been processed. These are not removed
    // directly since that would lead to ConcurrentModificationExceptions.
    private Map<Node, Set<Node>> calleesToBeRemoved = new IdentityHashMap<>();

    // Field read edges that should be removed when the reader has been processed. These are not
    // removed directly since that would lead to ConcurrentModificationExceptions.
    private Map<Node, Set<Node>> writersToBeRemoved = new IdentityHashMap<>();

    // Mapping from callee to the set of callers that were removed from the callee.
    private Map<DexEncodedMethod, ProgramMethodSet> removedCallEdges = new IdentityHashMap<>();

    // Set of nodes from which cycle elimination must be rerun to ensure that all cycles will be
    // removed.
    private LinkedHashSet<Node> revisit = new LinkedHashSet<>();

    CycleEliminationResult breakCycles(Collection<Node> roots) {
      // Break cycles in this call graph by removing edges causing cycles. We do this in a fixpoint
      // because the algorithm does not guarantee that all cycles will be removed from the graph
      // when we remove an edge in the middle of a cycle that contains another cycle.
      do {
        traverse(roots);
        roots = revisit;
        prepareForNewTraversal();
      } while (!roots.isEmpty());

      CycleEliminationResult result = new CycleEliminationResult(removedCallEdges);
      if (Log.ENABLED) {
        Log.info(getClass(), "# call graph cycles broken: %s", result.numberOfRemovedCallEdges());
      }
      reset();
      return result;
    }

    private void prepareForNewTraversal() {
      assert calleesToBeRemoved.isEmpty();
      assert stack.isEmpty();
      assert stackEntryInfo.isEmpty();
      assert writersToBeRemoved.isEmpty();
      assert writerStack.isEmpty();
      marked.clear();
      revisit = new LinkedHashSet<>();
    }

    private void reset() {
      assert marked.isEmpty();
      assert revisit.isEmpty();
      assert stack.isEmpty();
      assert stackEntryInfo.isEmpty();
      assert writerStack.isEmpty();
      removedCallEdges = new IdentityHashMap<>();
    }

    private static class WorkItem {
      boolean isNode() {
        return false;
      }

      NodeWorkItem asNode() {
        return null;
      }

      boolean isIterator() {
        return false;
      }

      IteratorWorkItem asIterator() {
        return null;
      }
    }

    private static class NodeWorkItem extends WorkItem {
      private final Node node;

      NodeWorkItem(Node node) {
        this.node = node;
      }

      @Override
      boolean isNode() {
        return true;
      }

      @Override
      NodeWorkItem asNode() {
        return this;
      }
    }

    private static class IteratorWorkItem extends WorkItem {
      private final Node callerOrReader;
      private final Iterator<Node> calleesAndWriters;

      IteratorWorkItem(Node callerOrReader, Iterator<Node> calleesAndWriters) {
        this.callerOrReader = callerOrReader;
        this.calleesAndWriters = calleesAndWriters;
      }

      @Override
      boolean isIterator() {
        return true;
      }

      @Override
      IteratorWorkItem asIterator() {
        return this;
      }
    }

    private void traverse(Collection<Node> roots) {
      Deque<WorkItem> workItems = new ArrayDeque<>(roots.size());
      for (Node node : roots) {
        workItems.addLast(new NodeWorkItem(node));
      }
      while (!workItems.isEmpty()) {
        WorkItem workItem = workItems.removeFirst();
        if (workItem.isNode()) {
          Node node = workItem.asNode().node;
          if (marked.contains(node)) {
            // Already visited all nodes that can be reached from this node.
            continue;
          }

          Node predecessor = stack.isEmpty() ? null : stack.peek();
          push(node, predecessor);

          // The callees and writers must be sorted before calling traverse recursively.
          // This ensures that cycles are broken the same way across multiple compilations.
          Iterator<Node> calleesAndWriterIterator =
              Iterators.concat(
                  node.getCalleesWithDeterministicOrder().iterator(),
                  node.getWritersWithDeterministicOrder().iterator());
          workItems.addFirst(new IteratorWorkItem(node, calleesAndWriterIterator));
        } else {
          assert workItem.isIterator();
          IteratorWorkItem iteratorWorkItem = workItem.asIterator();
          Node newCallerOrReader =
              iterateCalleesAndWriters(
                  iteratorWorkItem.calleesAndWriters, iteratorWorkItem.callerOrReader);
          if (newCallerOrReader != null) {
            // We did not finish the work on this iterator, so add it again.
            workItems.addFirst(iteratorWorkItem);
            workItems.addFirst(new NodeWorkItem(newCallerOrReader));
          } else {
            assert !iteratorWorkItem.calleesAndWriters.hasNext();
            pop(iteratorWorkItem.callerOrReader);
            marked.add(iteratorWorkItem.callerOrReader);

            Collection<Node> calleesToBeRemovedFromCaller =
                calleesToBeRemoved.remove(iteratorWorkItem.callerOrReader);
            if (calleesToBeRemovedFromCaller != null) {
              calleesToBeRemovedFromCaller.forEach(
                  callee -> {
                    callee.removeCaller(iteratorWorkItem.callerOrReader);
                    recordCallEdgeRemoval(iteratorWorkItem.callerOrReader, callee);
                  });
            }

            Collection<Node> writersToBeRemovedFromReader =
                writersToBeRemoved.remove(iteratorWorkItem.callerOrReader);
            if (writersToBeRemovedFromReader != null) {
              writersToBeRemovedFromReader.forEach(
                  writer -> writer.removeReader(iteratorWorkItem.callerOrReader));
            }
          }
        }
      }
    }

    private Node iterateCalleesAndWriters(
        Iterator<Node> calleeOrWriterIterator, Node callerOrReader) {
      while (calleeOrWriterIterator.hasNext()) {
        Node calleeOrWriter = calleeOrWriterIterator.next();
        StackEntryInfo calleeOrWriterStackEntryInfo = stackEntryInfo.get(calleeOrWriter);
        boolean foundCycle = calleeOrWriterStackEntryInfo != null;
        if (!foundCycle) {
          return calleeOrWriter;
        }

        // Found a cycle that needs to be eliminated. If it is a field read edge, then remove it
        // right away.
        boolean isFieldReadEdge = calleeOrWriter.hasReader(callerOrReader);
        if (isFieldReadEdge) {
          removeFieldReadEdge(callerOrReader, calleeOrWriter);
          continue;
        }

        // Otherwise, it is a call edge. Check if there is a field read edge in the cycle, and if
        // so, remove that edge.
        if (!writerStack.isEmpty()) {
          Node lastKnownWriter = writerStack.peek();
          StackEntryInfo lastKnownWriterStackEntryInfo = stackEntryInfo.get(lastKnownWriter);
          boolean cycleContainsLastKnownWriter =
              lastKnownWriterStackEntryInfo.index > calleeOrWriterStackEntryInfo.index;
          if (cycleContainsLastKnownWriter) {
            assert verifyCycleSatisfies(
                calleeOrWriter,
                cycle ->
                    cycle.contains(lastKnownWriter)
                        && cycle.contains(lastKnownWriterStackEntryInfo.predecessor));
            if (!lastKnownWriterStackEntryInfo.processed) {
              removeFieldReadEdge(lastKnownWriterStackEntryInfo.predecessor, lastKnownWriter);
              revisit.add(lastKnownWriter);
              lastKnownWriterStackEntryInfo.processed = true;
            }
            continue;
          }
        }

        // It is a call edge, and the cycle does not contain any field read edges. In this case, we
        // remove the call edge if it is safe according to force inlining.
        if (callEdgeRemovalIsSafe(callerOrReader, calleeOrWriter)) {
          // Break the cycle by removing the edge node->calleeOrWriter.
          // Need to remove `calleeOrWriter` from `node.callees` using the iterator to prevent a
          // ConcurrentModificationException.
          removeCallEdge(callerOrReader, calleeOrWriter);
          continue;
        }

        // The call edge cannot be removed due to force inlining. Find another call edge in the
        // cycle that can safely be removed instead.
        LinkedList<Node> cycle = extractCycle(calleeOrWriter);

        // Break the cycle by finding an edge that can be removed without breaking force
        // inlining. If that is not possible, this call fails with a compilation error.
        CallEdge edge = findCallEdgeForRemoval(cycle);

        // The edge will be null if this cycle has already been eliminated as a result of
        // another cycle elimination.
        if (edge != null) {
          assert callEdgeRemovalIsSafe(edge.caller, edge.callee);

          // Break the cycle by removing the edge caller->callee.
          removeCallEdge(edge.caller, edge.callee);
        }

        // Recover the stack.
        recoverStack(cycle);
      }
      return null;
    }

    private void push(Node node, Node predecessor) {
      stack.push(node);
      assert !stackEntryInfo.containsKey(node);
      stackEntryInfo.put(node, new StackEntryInfo(stack.size() - 1, predecessor));
      if (predecessor != null && predecessor.getWritersWithDeterministicOrder().contains(node)) {
        writerStack.push(node);
      }
    }

    private void pop(Node node) {
      Node popped = stack.pop();
      assert popped == node;
      assert stackEntryInfo.containsKey(node);
      stackEntryInfo.remove(node);
      if (writerStack.peek() == popped) {
        writerStack.pop();
      }
    }

    private void removeCallEdge(Node caller, Node callee) {
      calleesToBeRemoved.computeIfAbsent(caller, ignore -> Sets.newIdentityHashSet()).add(callee);
    }

    private void removeFieldReadEdge(Node reader, Node writer) {
      writersToBeRemoved.computeIfAbsent(reader, ignore -> Sets.newIdentityHashSet()).add(writer);
    }

    private LinkedList<Node> extractCycle(Node entry) {
      LinkedList<Node> cycle = new LinkedList<>();
      do {
        assert !stack.isEmpty();
        cycle.add(stack.pop());
      } while (cycle.getLast() != entry);
      return cycle;
    }

    private boolean verifyCycleSatisfies(Node entry, Predicate<LinkedList<Node>> predicate) {
      LinkedList<Node> cycle = extractCycle(entry);
      assert predicate.test(cycle);
      recoverStack(cycle);
      return true;
    }

    private CallEdge findCallEdgeForRemoval(LinkedList<Node> extractedCycle) {
      Node callee = extractedCycle.getLast();
      for (Node caller : extractedCycle) {
        if (caller.hasWriter(callee)) {
          // Not a call edge.
          assert !caller.hasCallee(callee);
          assert !callee.hasCaller(caller);
          callee = caller;
          continue;
        }
        if (!caller.hasCallee(callee)) {
          // No need to break any edges since this cycle has already been broken previously.
          assert !callee.hasCaller(caller);
          return null;
        }
        if (callEdgeRemovalIsSafe(caller, callee)) {
          return new CallEdge(caller, callee);
        }
        callee = caller;
      }
      throw new CompilationError(CYCLIC_FORCE_INLINING_MESSAGE);
    }

    private static boolean callEdgeRemovalIsSafe(Node callerOrReader, Node calleeOrWriter) {
      // All call edges where the callee is a method that should be force inlined must be kept,
      // to guarantee that the IR converter will process the callee before the caller.
      assert calleeOrWriter.hasCaller(callerOrReader);
      return !calleeOrWriter.getMethod().getOptimizationInfo().forceInline();
    }

    private void recordCallEdgeRemoval(Node caller, Node callee) {
      removedCallEdges
          .computeIfAbsent(callee.getMethod(), ignore -> ProgramMethodSet.create(2))
          .add(caller.getProgramMethod());
    }

    private void recoverStack(LinkedList<Node> extractedCycle) {
      Iterator<Node> descendingIt = extractedCycle.descendingIterator();
      while (descendingIt.hasNext()) {
        stack.push(descendingIt.next());
      }
    }
  }
}
