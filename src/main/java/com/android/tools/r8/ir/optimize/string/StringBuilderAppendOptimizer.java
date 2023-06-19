// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.ir.optimize.string.StringBuilderHelper.canMutate;
import static com.android.tools.r8.ir.optimize.string.StringBuilderNode.createAppendNode;
import static com.android.tools.r8.ir.optimize.string.StringBuilderNode.createEscapeNode;
import static com.android.tools.r8.ir.optimize.string.StringBuilderNode.createImplicitToStringNode;
import static com.android.tools.r8.ir.optimize.string.StringBuilderNode.createInitNode;
import static com.android.tools.r8.ir.optimize.string.StringBuilderNode.createInspectionNode;
import static com.android.tools.r8.ir.optimize.string.StringBuilderNode.createMutateNode;
import static com.android.tools.r8.ir.optimize.string.StringBuilderNode.createNewInstanceNode;
import static com.android.tools.r8.ir.optimize.string.StringBuilderNode.createOtherStringBuilderNode;
import static com.android.tools.r8.ir.optimize.string.StringBuilderNode.createToStringNode;
import static com.android.tools.r8.utils.FunctionUtils.ignoreArgument;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.DataflowAnalysisResult.SuccessfulDataflowAnalysisResult;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.IntraProceduralDataflowAnalysisOptions;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.IntraproceduralDataflowAnalysis;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.TransferFunctionResult;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.passes.CodeRewriterPass;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.optimize.string.StringBuilderNode.AppendNode;
import com.android.tools.r8.ir.optimize.string.StringBuilderNode.ImplicitToStringNode;
import com.android.tools.r8.ir.optimize.string.StringBuilderNode.InitNode;
import com.android.tools.r8.ir.optimize.string.StringBuilderNode.InitOrAppendNode;
import com.android.tools.r8.ir.optimize.string.StringBuilderNode.LoopNode;
import com.android.tools.r8.ir.optimize.string.StringBuilderNode.NewInstanceNode;
import com.android.tools.r8.ir.optimize.string.StringBuilderNode.SplitReferenceNode;
import com.android.tools.r8.ir.optimize.string.StringBuilderNodeMuncher.MunchingState;
import com.android.tools.r8.ir.optimize.string.StringBuilderOracle.DefaultStringBuilderOracle;
import com.android.tools.r8.utils.DepthFirstSearchWorkListBase.DepthFirstSearchWorkList;
import com.android.tools.r8.utils.DepthFirstSearchWorkListBase.StatefulDepthFirstSearchWorkList;
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Reference2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap.Entry;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * {@link StringBuilderAppendOptimizer} will try to optimize string builders by joining appends with
 * constant arguments and materializing toStrings into constant strings to be able to remove entire
 * StringBuilders (or StringBuffers).
 *
 * <p>The algorithm works by producing graphs for all string builders of a method. The graphs follow
 * the control flow of the method, thus, if a string builder is assigned a value in two blocks from
 * a certain point, and there is no linear path between the two assignments, they will show up in a
 * graph as two successor nodes to a common predecessor. StringBuilder graphs are not tracked
 * through phis, instead a phi and subsequent uses of that phi will also occur as a graph.
 *
 * <p>When all graphs are constructed the graphs are optimized by running a munching algorithm on
 * them.
 *
 * <p>Finally, based on all optimizations, the IR is updated to reflect the optimizations.
 */
public class StringBuilderAppendOptimizer extends CodeRewriterPass<AppInfo> {

  private final StringBuilderOracle oracle;
  private static final int NUMBER_OF_MUNCHING_PASSES = 3;

  public StringBuilderAppendOptimizer(AppView<?> appView) {
    super(appView);
    oracle = new DefaultStringBuilderOracle(appView.dexItemFactory());
  }

  @Override
  protected String getTimingId() {
    return "StringBuilderAppendOptimizer";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code) {
    return options.enableStringConcatenationOptimization
        && !isDebugMode(code.context())
        && (code.metadata().mayHaveNewInstance()
            || code.metadata().mayHaveInvokeMethodWithReceiver());
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    Map<Value, StringBuilderNode> stringBuilderGraphs = computeStringBuilderGraphs(code);
    Map<Instruction, StringBuilderAction> actions = optimizeOnGraphs(code, stringBuilderGraphs);
    if (actions.isEmpty()) {
      return CodeRewriterResult.NO_CHANGE;
    }
    InstructionListIterator it = code.instructionListIterator();
    while (it.hasNext()) {
      Instruction instruction = it.next();
      StringBuilderAction stringBuilderAction = actions.get(instruction);
      if (stringBuilderAction != null) {
        stringBuilderAction.perform(appView, code, it, instruction, oracle);
      }
    }
    code.removeAllDeadAndTrivialPhis();
    return CodeRewriterResult.HAS_CHANGED;
  }

  private static class StringBuilderGraphState {

    private final Map<Value, StringBuilderNode> roots;
    private final Map<Value, StringBuilderNode> tails;
    private boolean isPartOfLoop;

    private StringBuilderGraphState(
        Map<Value, StringBuilderNode> roots, Map<Value, StringBuilderNode> tails) {
      this.roots = roots;
      this.tails = tails;
    }
  }

  /***
   * This will compute a collection of graphs from StringBuilders and return a map from value to
   * the root of the string builder graph.
   *
   * The graphs are constructed by doing a DFS traversel and computing all string builder actions
   * inside a block. The flow here is known to be linear. When backtracking, the linear collection
   * of nodes (represented by root and tail) are then combined in a way that ensure the control flow
   * of the method is maintained in the graph.
   *
   * To ensure that we correctly compute when a string builder escapes and when it can be mutated
   * we first compute a {@link StringBuilderEscapeState} by using a flow analysis, enabling us to
   * answer, for a given instruction, is a string builder value escaping and all escaped values
   * at the instruction.
   */
  private Map<Value, StringBuilderNode> computeStringBuilderGraphs(IRCode code) {
    StringBuilderEscapeTransferFunction transferFunction =
        new StringBuilderEscapeTransferFunction(oracle);
    IntraproceduralDataflowAnalysis<StringBuilderEscapeState> analysis =
        new IntraproceduralDataflowAnalysis<>(
            appView,
            StringBuilderEscapeState.bottom(),
            code,
            transferFunction,
            IntraProceduralDataflowAnalysisOptions.getNoCollapseInstance());
    SuccessfulDataflowAnalysisResult<?, StringBuilderEscapeState> stringBuilderEscapeResult =
        analysis.run(code.entryBlock()).asSuccessfulAnalysisResult();

    if (stringBuilderEscapeResult == null) {
      return Collections.emptyMap();
    }

    TraversalContinuation<Void, StringBuilderGraphState> graphResult =
        new StatefulDepthFirstSearchWorkList<BasicBlock, StringBuilderGraphState, Void>() {

          @Override
          @SuppressWarnings("ReturnValueIgnored")
          protected TraversalContinuation<Void, StringBuilderGraphState> process(
              DFSNodeWithState<BasicBlock, StringBuilderGraphState> node,
              Function<BasicBlock, DFSNodeWithState<BasicBlock, StringBuilderGraphState>>
                  childNodeConsumer) {
            Map<Value, StringBuilderNode> currentRoots = new LinkedHashMap<>();
            Map<Value, StringBuilderNode> currentTails = new IdentityHashMap<>();
            BasicBlock block = node.getNode();
            StringBuilderEscapeState previousState = analysis.computeBlockEntryState(block);
            TransferFunctionResult<StringBuilderEscapeState> result =
                transferFunction.applyBlock(block, previousState);
            if (result.isFailedTransferResult()) {
              assert false : "Computing the state should never fail";
              return TraversalContinuation.doBreak();
            }
            previousState = result.asAbstractState();
            for (Phi phi : block.getPhis()) {
              if (previousState.isLiveStringBuilder(phi)) {
                boolean seenEscaped =
                    visitAllAliasing(
                        phi,
                        previousState,
                        value -> {},
                        alias ->
                            addNodeToRootAndTail(
                                currentRoots, currentTails, alias, createEscapeNode()));
                if (seenEscaped) {
                  addNodeToRootAndTail(currentRoots, currentTails, phi, createEscapeNode());
                }
              }
            }
            for (Instruction instruction : block.getInstructions()) {
              result = transferFunction.apply(instruction, previousState);
              if (result.isFailedTransferResult()) {
                assert false : "Computing the state should never fail";
                return TraversalContinuation.doBreak();
              }
              previousState = result.asAbstractState();
              createNodesForInstruction(
                  instruction,
                  previousState,
                  (value, sbNode) ->
                      addNodeToRootAndTail(currentRoots, currentTails, value, sbNode));
            }
            assert currentRoots.keySet().equals(currentTails.keySet());
            assert previousState.getLiveStringBuilders().containsAll(currentRoots.keySet())
                : "Seen root that is not a live string builder";
            node.setState(new StringBuilderGraphState(currentRoots, currentTails));
            for (BasicBlock successor : block.getSuccessors()) {
              childNodeConsumer.apply(successor);
            }
            return TraversalContinuation.doContinue();
          }

          private void addNodeToRootAndTail(
              Map<Value, StringBuilderNode> currentRoots,
              Map<Value, StringBuilderNode> currentTails,
              Value value,
              StringBuilderNode node) {
            StringBuilderNode currentTail = currentTails.get(value);
            if (currentTail == null) {
              currentRoots.put(value, node);
              currentTails.put(value, node);
            } else if (shouldAddNodeToGraph(currentTail, node)) {
              currentTail.addSuccessor(node);
              currentTails.put(value, node);
            }
          }

          private boolean shouldAddNodeToGraph(
              StringBuilderNode insertedNode, StringBuilderNode newNode) {
            // No need for multiple mutating nodes or inspecting nodes.
            if (insertedNode.isMutateNode()) {
              return !newNode.isMutateNode() && !newNode.isInspectingNode();
            } else if (insertedNode.isInspectingNode()) {
              return !newNode.isInspectingNode();
            } else if (insertedNode.isEscapeNode()) {
              return !newNode.isEscapeNode();
            }
            return true;
          }

          private void createNodesForInstruction(
              Instruction instruction,
              StringBuilderEscapeState escapeState,
              BiConsumer<Value, StringBuilderNode> nodeConsumer) {
            // Do not build nodes for assume values.
            if (instruction.isAssume()) {
              return;
            }
            if (oracle.isModeledStringBuilderInstruction(
                instruction, escapeState::isLiveStringBuilder)) {
              createNodesForStringBuilderInstruction(instruction, escapeState, nodeConsumer);
            } else {
              for (Value newEscapedValue : escapeState.getNewlyEscaped()) {
                visitStringBuilderValues(
                    newEscapedValue,
                    escapeState,
                    nonAlias -> nodeConsumer.accept(nonAlias, createEscapeNode()),
                    alias -> nodeConsumer.accept(alias, createEscapeNode()));
              }
              if (canMutate(instruction)) {
                for (Value escapedStringBuilder : escapeState.getEscaping()) {
                  visitStringBuilderValues(
                      escapedStringBuilder,
                      escapeState,
                      nonAlias -> nodeConsumer.accept(nonAlias, createMutateNode()),
                      alias -> nodeConsumer.accept(alias, createMutateNode()));
                }
              }
            }
          }

          private void createNodesForStringBuilderInstruction(
              Instruction instruction,
              StringBuilderEscapeState escapeState,
              BiConsumer<Value, StringBuilderNode> nodeConsumer) {
            if (instruction.isNewInstance()) {
              Value newInstanceValue = instruction.outValue();
              assert newInstanceValue != null;
              nodeConsumer.accept(
                  newInstanceValue, createNewInstanceNode(instruction.asNewInstance()));
            } else if (instruction.isInvokeMethodWithReceiver()) {
              InvokeMethodWithReceiver invoke = instruction.asInvokeMethodWithReceiver();
              Value receiver = invoke.getReceiver();
              if (oracle.isInit(instruction)) {
                InitNode initNode = createInitNode(instruction.asInvokeDirect());
                String constantArgument = oracle.getConstantArgument(instruction);
                initNode.setConstantArgument(constantArgument);
                if (constantArgument == null
                    && oracle.isStringConstructor(instruction)
                    && invoke.getFirstNonReceiverArgument().isNeverNull()) {
                  initNode.setNonConstantArgument(invoke.getFirstNonReceiverArgument());
                }
                if (invoke.arguments().size() == 2) {
                  Value arg = invoke.getOperand(1);
                  if (oracle.hasStringBuilderType(arg)) {
                    insertImplicitToStringNode(
                        arg, instruction, initNode, escapeState, nodeConsumer);
                  }
                }
                visitStringBuilderValues(
                    receiver,
                    escapeState,
                    actual -> nodeConsumer.accept(actual, initNode),
                    escaped -> nodeConsumer.accept(escaped, createInspectionNode(instruction)));
              } else if (oracle.isAppend(instruction)) {
                AppendNode appendNode = createAppendNode(instruction.asInvokeVirtual());
                String constantArgument = oracle.getConstantArgument(instruction);
                appendNode.setConstantArgument(constantArgument);
                Value arg = invoke.getFirstNonReceiverArgument();
                if (constantArgument == null
                    && oracle.isAppendString(instruction)
                    && arg.isNeverNull()) {
                  appendNode.setNonConstantArgument(arg);
                }
                if (oracle.hasStringBuilderType(arg.getAliasedValue())) {
                  insertImplicitToStringNode(
                      arg, instruction, appendNode, escapeState, nodeConsumer);
                }
                visitStringBuilderValues(
                    receiver,
                    escapeState,
                    actual -> nodeConsumer.accept(actual, appendNode),
                    escaped -> nodeConsumer.accept(escaped, createMutateNode()));
              } else if (oracle.isToString(instruction, receiver)) {
                visitStringBuilderValues(
                    receiver,
                    escapeState,
                    actual ->
                        nodeConsumer.accept(
                            actual, createToStringNode(instruction.asInvokeVirtual())),
                    escaped -> nodeConsumer.accept(escaped, createInspectionNode(instruction)));
              } else if (oracle.isInspecting(instruction)) {
                visitStringBuilderValues(
                    receiver,
                    escapeState,
                    actual -> nodeConsumer.accept(actual, createInspectionNode(instruction)),
                    escaped -> nodeConsumer.accept(escaped, createInspectionNode(instruction)));
              } else {
                visitStringBuilderValues(
                    receiver,
                    escapeState,
                    actual ->
                        nodeConsumer.accept(actual, createOtherStringBuilderNode(instruction)),
                    escaped ->
                        nodeConsumer.accept(escaped, createOtherStringBuilderNode(instruction)));
              }
            } else {
              assert instruction.isInvokeStatic();
              InvokeStatic invoke = instruction.asInvokeStatic();
              assert invoke.getInvokedMethod()
                  == appView.dexItemFactory().objectsMethods.toStringWithObject;
              visitStringBuilderValues(
                  invoke.getFirstOperand(),
                  escapeState,
                  actual ->
                      nodeConsumer.accept(actual, createToStringNode(instruction.asInvokeMethod())),
                  escaped -> nodeConsumer.accept(escaped, createInspectionNode(instruction)));
            }
          }

          // For tracking propagating constant string builder values through append or init we
          // build a link between the two graphs.
          private void insertImplicitToStringNode(
              Value value,
              Instruction instruction,
              InitOrAppendNode node,
              StringBuilderEscapeState escapeState,
              BiConsumer<Value, StringBuilderNode> nodeConsumer) {
            assert escapeState.isLiveStringBuilder(value);
            ImplicitToStringNode implicitToStringNode = createImplicitToStringNode(node);
            visitStringBuilderValues(
                value,
                escapeState,
                actual -> nodeConsumer.accept(actual, implicitToStringNode),
                escaped -> nodeConsumer.accept(escaped, createInspectionNode(instruction)));
            node.setImplicitToStringNode(implicitToStringNode);
          }

          private void visitStringBuilderValues(
              Value value,
              StringBuilderEscapeState state,
              Consumer<Value> actualConsumer,
              Consumer<Value> aliasAndEscapedConsumer) {
            assert state.isLiveStringBuilder(value);
            boolean seenEscaped =
                visitAllAliasing(value, state, actualConsumer, aliasAndEscapedConsumer);
            seenEscaped |= visitAllAliases(value, state, aliasAndEscapedConsumer);
            if (seenEscaped) {
              for (Value escapingValue : state.getEscaping()) {
                aliasAndEscapedConsumer.accept(escapingValue);
              }
            }
          }

          private boolean visitAllAliasing(
              Value value,
              StringBuilderEscapeState state,
              Consumer<Value> nonAliasConsumer,
              Consumer<Value> aliasAndEscapedConsumer) {
            WorkList<Value> valueWorkList = WorkList.newIdentityWorkList(value);
            boolean seenUnknownAlias = false;
            boolean seenEscaped = false;
            while (valueWorkList.hasNext()) {
              Value next = valueWorkList.next();
              seenEscaped |= state.isEscaped(next);
              Set<Value> aliasing =
                  state.getAliasesToDefinitions().getOrDefault(next, Collections.emptySet());
              valueWorkList.addIfNotSeen(aliasing);
              // Check if we have a direct alias such as Assume, CheckCast or StringBuilder.append.
              if (aliasing.size() != 1 || next.isPhi()) {
                if (!seenUnknownAlias) {
                  nonAliasConsumer.accept(next);
                  seenUnknownAlias = true;
                } else {
                  aliasAndEscapedConsumer.accept(next);
                }
              }
            }
            return seenEscaped;
          }

          private boolean visitAllAliases(
              Value value, StringBuilderEscapeState state, Consumer<Value> aliasConsumer) {
            Map<Value, Set<Value>> escapedDefinitionsToKnown = state.getDefinitionsToAliases();
            WorkList<Value> valueWorkList =
                WorkList.newIdentityWorkList(
                    escapedDefinitionsToKnown.getOrDefault(value, Collections.emptySet()));
            boolean seenEscaped = false;
            while (valueWorkList.hasNext()) {
              Value next = valueWorkList.next();
              seenEscaped |= state.isEscaped(next);
              if (next.isPhi()) {
                aliasConsumer.accept(next);
              }
              valueWorkList.addIfNotSeen(
                  escapedDefinitionsToKnown.getOrDefault(next, Collections.emptySet()));
            }
            return seenEscaped;
          }

          @Override
          protected TraversalContinuation<Void, StringBuilderGraphState> joiner(
              DFSNodeWithState<BasicBlock, StringBuilderGraphState> node,
              List<DFSNodeWithState<BasicBlock, StringBuilderGraphState>> childStates) {
            StringBuilderGraphState state = node.getState();
            Reference2IntMap<Value> rootsInChildStateCounts =
                new Reference2IntLinkedOpenHashMap<>();
            for (DFSNodeWithState<BasicBlock, StringBuilderGraphState> childState : childStates) {
              StringBuilderGraphState childGraphState = childState.getState();
              childGraphState.roots.forEach(
                  (value, sbNode) -> {
                    rootsInChildStateCounts.put(value, rootsInChildStateCounts.getInt(value) + 1);
                    StringBuilderNode currentRoot = state.roots.get(value);
                    StringBuilderNode currentTail = state.tails.get(value);
                    if (currentRoot == null) {
                      assert currentTail == null;
                      if (childStates.size() == 1) {
                        state.roots.put(value, sbNode);
                        state.tails.put(value, sbNode);
                        return;
                      }
                      // We are adding a value coming only from a child state and not defined here.
                      // To ensure proper ordering we add a sentinel node. If it turns out there is
                      // only a single reference, we rely on munching to remove it.
                      currentRoot = StringBuilderNode.createSplitReferenceNode();
                      currentTail = currentRoot;
                      state.roots.put(value, currentRoot);
                      state.tails.put(value, currentTail);
                    }
                    assert currentTail != null;
                    // Link next node from successor
                    currentTail.addSuccessor(sbNode);
                    sbNode.addPredecessor(currentTail);
                  });
              if (childState.seenAndNotProcessed()) {
                childGraphState.isPartOfLoop = true;
              }
            }
            // To ensure that we account for control flow correctly, we insert split reference nodes
            // for all roots we've seen in only a subset of child states.
            for (Entry<Value> valueEntry : rootsInChildStateCounts.reference2IntEntrySet()) {
              assert valueEntry.getIntValue() <= childStates.size();
              if (valueEntry.getIntValue() < childStates.size()) {
                SplitReferenceNode splitNode = StringBuilderNode.createSplitReferenceNode();
                StringBuilderNode tail = state.tails.get(valueEntry.getKey());
                assert tail != null;
                splitNode.addPredecessor(tail);
                tail.addSuccessor(splitNode);
              }
            }
            if (state.isPartOfLoop) {
              state.roots.replaceAll(
                  (value, currentRoot) -> {
                    LoopNode loopNode = StringBuilderNode.createLoopNode();
                    loopNode.addSuccessor(currentRoot);
                    return loopNode;
                  });
            }
            return TraversalContinuation.doContinue(state);
          }
        }.run(code.entryBlock());

    return graphResult.shouldBreak()
        ? Collections.emptyMap()
        : graphResult.asContinue().getValue().roots;
  }

  /**
   * optimizeOnGraphs will compute some state that will make munching easier. When computing the
   * state the search will also do a topological sort over string builder references such that
   * string builders without a direct dependency on another string builder will be computed first.
   *
   * <p>In general, this would not really matter since we munch over all graphs, but we are limiting
   * the munching and care about performance.
   */
  private Map<Instruction, StringBuilderAction> optimizeOnGraphs(
      IRCode code, Map<Value, StringBuilderNode> stringBuilderGraphs) {
    Map<Instruction, StringBuilderAction> actions = new IdentityHashMap<>();
    // Build state to allow munching over the string builder graphs.
    Map<StringBuilderNode, NewInstanceNode> newInstances = new IdentityHashMap<>();
    Set<StringBuilderNode> inspectingCapacity = Sets.newIdentityHashSet();
    Set<StringBuilderNode> looping = Sets.newIdentityHashSet();
    Map<StringBuilderNode, Set<StringBuilderNode>> materializing = new IdentityHashMap<>();
    Set<StringBuilderNode> escaping = Sets.newIdentityHashSet();

    Map<StringBuilderNode, StringBuilderNode> nodeToRoots = new IdentityHashMap<>();
    Map<StringBuilderNode, Set<StringBuilderNode>> stringBuilderDependencies =
        new IdentityHashMap<>();

    stringBuilderGraphs.forEach(
        (value, root) -> {
          WorkList<StringBuilderNode> workList = WorkList.newIdentityWorkList(root);
          Set<StringBuilderNode> materializingInstructions = Sets.newIdentityHashSet();
          materializing.put(root, materializingInstructions);
          while (workList.hasNext()) {
            StringBuilderNode next = workList.next();
            nodeToRoots.put(next, root);
            if (next.isNewInstanceNode()) {
              StringBuilderNode existing = newInstances.put(root, next.asNewInstanceNode());
              assert existing == null;
            }
            if (next.isInitOrAppend()) {
              ImplicitToStringNode dependency = next.asInitOrAppend().getImplicitToStringNode();
              if (dependency != null) {
                stringBuilderDependencies
                    .computeIfAbsent(root, ignoreArgument(Sets::newIdentityHashSet))
                    .add(dependency);
              }
            }
            if (next.isLoopNode()) {
              looping.add(root);
            }
            if (next.isEscapeNode()) {
              inspectingCapacity.add(root);
              escaping.add(root);
            }
            if (next.isToStringNode() || next.isImplicitToStringNode()) {
              materializingInstructions.add(next);
            }
            if (next.isInspectingNode()) {
              inspectingCapacity.add(root);
            }
            next.getSuccessors().forEach(workList::addFirstIfNotSeen);
          }
        });

    MunchingState munchingState =
        new MunchingState(
            actions,
            escaping,
            inspectingCapacity,
            looping,
            materializing,
            newInstances,
            oracle,
            () -> code.createValue(TypeElement.stringClassType(appView)));

    boolean keepMunching = true;
    for (int i = 0; i < NUMBER_OF_MUNCHING_PASSES && keepMunching; i++) {
      keepMunching = false;
      for (StringBuilderNode root :
          computeProcessingOrder(stringBuilderGraphs, stringBuilderDependencies, nodeToRoots)) {
        WorkList<StringBuilderNode> workList = WorkList.newIdentityWorkList(root);
        while (workList.hasNext()) {
          StringBuilderNode next = workList.next();
          keepMunching |= StringBuilderNodeMuncher.optimize(root, next, munchingState);
          next.getSuccessors().forEach(workList::addFirstIfNotSeen);
        }
      }
    }
    return actions;
  }

  private Collection<StringBuilderNode> computeProcessingOrder(
      Map<Value, StringBuilderNode> stringBuilderGraphs,
      Map<StringBuilderNode, Set<StringBuilderNode>> stringBuilderDependencies,
      Map<StringBuilderNode, StringBuilderNode> nodeToRoots) {
    // Make a topological sort to ensure we visit all nodes in the best order for optimizing nested
    // string builders.
    Set<StringBuilderNode> processingOrder = new LinkedHashSet<>();
    new DepthFirstSearchWorkList<StringBuilderNode, Void, Void>() {

      @Override
      @SuppressWarnings("ReturnValueIgnored")
      protected TraversalContinuation<Void, Void> process(
          DFSNode<StringBuilderNode> node,
          Function<StringBuilderNode, DFSNode<StringBuilderNode>> childNodeConsumer) {
        StringBuilderNode root = node.getNode();
        Set<StringBuilderNode> stringBuilderNodes = stringBuilderDependencies.get(root);
        if (stringBuilderNodes != null) {
          for (StringBuilderNode dependency : stringBuilderNodes) {
            childNodeConsumer.apply(nodeToRoots.get(dependency));
          }
        }
        return TraversalContinuation.doContinue();
      }

      @Override
      protected List<Void> getFinalStateForRoots(Collection<? extends StringBuilderNode> roots) {
        return null;
      }

      @Override
      public TraversalContinuation<Void, Void> joiner(DFSNode<StringBuilderNode> node) {
        StringBuilderNode node1 = node.getNode();
        processingOrder.add(node1);
        return TraversalContinuation.doContinue();
      }
    }.run(stringBuilderGraphs.values());
    return processingOrder;
  }
}
