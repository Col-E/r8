// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.string;

import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.string.StringBuilderAction.AppendWithNewConstantString;
import com.android.tools.r8.ir.optimize.string.StringBuilderAction.RemoveStringBuilderAction;
import com.android.tools.r8.ir.optimize.string.StringBuilderAction.ReplaceByConstantString;
import com.android.tools.r8.ir.optimize.string.StringBuilderNode.AppendNode;
import com.android.tools.r8.ir.optimize.string.StringBuilderNode.ImplicitToStringNode;
import com.android.tools.r8.ir.optimize.string.StringBuilderNode.InitNode;
import com.android.tools.r8.ir.optimize.string.StringBuilderNode.InitOrAppend;
import com.android.tools.r8.ir.optimize.string.StringBuilderNode.StringBuilderInstruction;
import com.android.tools.r8.ir.optimize.string.StringBuilderNode.ToStringNode;
import com.android.tools.r8.utils.WorkList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * StringBuilderNodeMuncher is a classic munching algorithm that will try to remove nodes on string
 * builders by looking at small view of it {@link PeepholePattern}. If a pattern can be optimized
 * the graph will be updated in place and if the IR needs to be updated, an action will be added.
 */
class StringBuilderNodeMuncher {

  static class MunchingState {

    private final Map<Instruction, StringBuilderAction> actions;
    private final StringBuilderOracle oracle;

    // The below state can all be computed by searching the graph but are tracked here to improve
    // performance.
    private final Set<StringBuilderNode> escaping;
    private final Set<StringBuilderNode> inspectingCapacity;
    private final Set<StringBuilderNode> looping;
    private final Map<StringBuilderNode, Set<StringBuilderNode>> materializingInstructions;
    private final Map<Value, String> optimizedStrings = new IdentityHashMap<>();

    MunchingState(
        Map<Instruction, StringBuilderAction> actions,
        Set<StringBuilderNode> escaping,
        Set<StringBuilderNode> inspectingCapacity,
        Set<StringBuilderNode> looping,
        Map<StringBuilderNode, Set<StringBuilderNode>> materializingInstructions,
        StringBuilderOracle oracle) {
      this.actions = actions;
      this.escaping = escaping;
      this.inspectingCapacity = inspectingCapacity;
      this.looping = looping;
      this.materializingInstructions = materializingInstructions;
      this.oracle = oracle;
    }
  }

  private interface PeepholePattern {

    boolean optimize(
        StringBuilderNode root, StringBuilderNode currentNode, MunchingState munchingState);
  }

  /**
   * This peephole will try to optimize two sequential appends:
   *
   * <pre>
   * append("foo") -> append("bar") =>
   * append("foobar")
   *
   * or
   *
   * init("foo") -> append("bar") =>
   * init("foobar")
   * </pre>
   */
  private static class MunchAppends implements PeepholePattern {

    @Override
    public boolean optimize(
        StringBuilderNode root, StringBuilderNode currentNode, MunchingState munchingState) {
      if (!currentNode.isAppendNode()) {
        return false;
      }
      String currentConstantArgument = getConstantArgumentForNode(currentNode, munchingState);
      if (currentConstantArgument == null || !currentNode.hasSinglePredecessor()) {
        return false;
      }
      StringBuilderNode previous = currentNode.getSinglePredecessor();
      String previousConstantArgument = getConstantArgumentForNode(previous, munchingState);
      if (previousConstantArgument == null || !previous.hasSingleSuccessor()) {
        return false;
      }
      // The capacity changes based on the init call (on JVM it adds 16 to length of input).
      if (previous.isInitNode() && munchingState.inspectingCapacity.contains(root)) {
        return false;
      }
      assert previous.isInitOrAppend();
      String newConstant = previousConstantArgument + currentConstantArgument;
      InitOrAppend initOrAppend = previous.asInitOrAppend();
      initOrAppend.setConstantArgument(newConstant);
      munchingState.actions.put(
          initOrAppend.getInstruction(), new AppendWithNewConstantString(newConstant));
      munchingState.actions.put(
          currentNode.asAppendNode().getInstruction(), RemoveStringBuilderAction.getInstance());
      currentNode.removeNode();
      return true;
    }
  }

  /**
   * This peephole will try to remove toString nodes and replace by a constant string:
   *
   * <pre>
   * newInstance -> init("foo") -> append("bar") -> toString() =>
   * newInstance -> init("foo") -> append("bar")
   * </pre>
   *
   * <p>If the node is an implicitToString, we update the append of another builder to have the new
   * constant value directly. If not, we keep track of the outValue toString() had is replaced by a
   * constant, by updating {@code MunchingState.optimizedStrings}
   */
  private static class MunchToString implements PeepholePattern {

    @Override
    public boolean optimize(
        StringBuilderNode originalRoot,
        StringBuilderNode currentNode,
        MunchingState munchingState) {
      if (!currentNode.isToStringNode() && !currentNode.isImplicitToStringNode()) {
        return false;
      }
      StringBuilderNode root = findFirstNonSentinelRoot(originalRoot);
      if (!root.isNewInstanceNode() || !root.hasSingleSuccessor()) {
        return false;
      }
      StringBuilderNode init = root.getSingleSuccessor();
      String rootConstantArgument = getConstantArgumentForNode(init, munchingState);
      if (rootConstantArgument == null || !init.isInitNode()) {
        return false;
      }
      // This is either <init>(str) -> toString() or <init>(str) -> append(str) -> toString()
      // If the string builder dependency is directly given to another string builder, there is
      // no toString() but an append with this string builder as argument.
      if (!currentNode.hasSinglePredecessor() || !init.hasSingleSuccessor()) {
        return false;
      }
      String constantArgument = null;
      if (currentNode.getSinglePredecessor() == init) {
        constantArgument = rootConstantArgument;
      } else {
        StringBuilderNode expectedAppend = init.getSingleSuccessor();
        StringBuilderNode expectedSameAppend = currentNode.getSinglePredecessor();
        String appendConstantArgument = getConstantArgumentForNode(expectedAppend, munchingState);
        if (expectedAppend == expectedSameAppend && appendConstantArgument != null) {
          // TODO(b/190489514): See if this larger pattern is necessary.
          assert false : "See why this larger pattern is necessary";
          constantArgument = rootConstantArgument + appendConstantArgument;
        }
      }
      if (constantArgument == null) {
        return false;
      }
      if (currentNode.isToStringNode()) {
        ToStringNode toStringNode = currentNode.asToStringNode();
        munchingState.actions.put(
            toStringNode.getInstruction(), new ReplaceByConstantString(constantArgument));
        munchingState.materializingInstructions.get(originalRoot).remove(currentNode);
        String oldValue =
            munchingState.optimizedStrings.put(
                toStringNode.getInstruction().outValue(), constantArgument);
        assert oldValue == null;
      } else {
        assert currentNode.isImplicitToStringNode();
        ImplicitToStringNode implicitToStringNode = currentNode.asImplicitToStringNode();
        InitOrAppend initOrAppend = implicitToStringNode.getInitOrAppend();
        initOrAppend.setConstantArgument(constantArgument);
        munchingState.actions.put(
            initOrAppend.getInstruction(), new AppendWithNewConstantString(constantArgument));
      }
      currentNode.removeNode();
      return true;
    }
  }

  /**
   * Find the first non split reference node or loop-node, which are nodes inserted to track
   * control-flow.
   */
  private static StringBuilderNode findFirstNonSentinelRoot(StringBuilderNode root) {
    WorkList<StringBuilderNode> workList = WorkList.newIdentityWorkList(root);
    while (workList.hasNext()) {
      StringBuilderNode node = workList.next();
      if (!node.isSplitReferenceNode() && !node.isLoopNode()) {
        return node;
      }
      if (node.hasSingleSuccessor()) {
        workList.addIfNotSeen(node.getSingleSuccessor());
      }
    }
    return root;
  }

  private static String getConstantArgumentForNode(
      StringBuilderNode node, MunchingState munchingState) {
    if (node.isAppendNode()) {
      AppendNode appendNode = node.asAppendNode();
      if (appendNode.hasConstantArgument()) {
        return appendNode.getConstantArgument();
      }
      return getOptimizedConstantArgument(appendNode, munchingState);
    } else if (node.isInitNode()) {
      InitNode initNode = node.asInitNode();
      if (initNode.hasConstantArgument()) {
        return initNode.getConstantArgument();
      }
      return getOptimizedConstantArgument(initNode, munchingState);
    }
    return null;
  }

  private static String getOptimizedConstantArgument(
      StringBuilderInstruction node, MunchingState munchingState) {
    List<Value> inValues = node.getInstruction().inValues();
    if (inValues.size() != 2) {
      return null;
    }
    return munchingState.optimizedStrings.get(inValues.get(1).getAliasedValue());
  }

  /**
   * This pattern tries to remove nodes that are no longer needed. An example is when a toString is
   * materialized, and append may now not be observable and can be removed. This pattern tries to
   * remove as many nodes as possibly by continuing to remove predecessor nodes. This is not
   * necessary for correctness but since the overall munching algorithm runs over successors,
   * removing predecessors directly here saves cycles.
   */
  private static class MunchNonMaterializing implements PeepholePattern {

    @Override
    public boolean optimize(
        StringBuilderNode root, StringBuilderNode currentNode, MunchingState munchingState) {
      boolean removedAnyNodes = false;
      boolean removeNode;
      boolean isEscaping = munchingState.escaping.contains(root);
      while (currentNode != null) {
        // Remove appends if the string builder do not escape, is not inspected or materialized
        // and if it is not part of a loop.
        removeNode = false;
        if (currentNode.isSplitReferenceNode()) {
          removeNode = currentNode.getSuccessors().isEmpty() || currentNode.hasSinglePredecessor();
        } else if (currentNode.isAppendNode() && !isEscaping) {
          AppendNode appendNode = currentNode.asAppendNode();
          boolean canRemoveIfNoInspectionOrMaterializing =
              !munchingState.inspectingCapacity.contains(root)
                  && munchingState.materializingInstructions.get(root).isEmpty();
          boolean canRemoveIfLastAndNoLoop =
              !isLoopingOnPath(root, currentNode, munchingState)
                  && currentNode.getSuccessors().isEmpty();
          if (canRemoveIfNoInspectionOrMaterializing
              && canRemoveIfLastAndNoLoop
              && munchingState.oracle.canObserveStringBuilderCall(
                  currentNode.asAppendNode().getInstruction())) {
            munchingState.actions.put(
                appendNode.getInstruction(), RemoveStringBuilderAction.getInstance());
            removeNode = true;
          }
        } else if (currentNode.isInitNode()
            && currentNode.asInitNode().hasConstantArgument()
            && currentNode.hasSinglePredecessor()
            && currentNode.getSinglePredecessor().isNewInstanceNode()
            && currentNode.getSuccessors().isEmpty()
            && !isEscaping
            && !munchingState.oracle.canObserveStringBuilderCall(
                currentNode.asInitNode().getInstruction())) {
          removeNode = true;
        }
        if (!removeNode) {
          return false;
        } else {
          removedAnyNodes = true;
          currentNode.removeNode();
          if (currentNode.isStringBuilderInstructionNode()) {
            munchingState.actions.put(
                currentNode.asStringBuilderInstructionNode().getInstruction(),
                RemoveStringBuilderAction.getInstance());
          }
          currentNode =
              currentNode.hasSinglePredecessor() ? currentNode.getSinglePredecessor() : null;
        }
      }
      return removedAnyNodes;
    }

    private boolean isLoopingOnPath(
        StringBuilderNode root, StringBuilderNode currentNode, MunchingState munchingState) {
      if (!munchingState.looping.contains(root)) {
        return false;
      }
      WorkList<StringBuilderNode> workList = WorkList.newIdentityWorkList(currentNode);
      boolean seenNewInstance = false;
      while (workList.hasNext()) {
        StringBuilderNode next = workList.next();
        if (next.isNewInstanceNode()) {
          seenNewInstance = true;
        }
        if (next.isLoopNode()) {
          // If we have seen a new instance and there is only a single successor for the loop
          // the instance is always created inside the body.
          return !seenNewInstance || !next.hasSingleSuccessor();
        }
        workList.addIfNotSeen(next.getPredecessors());
      }
      return false;
    }
  }

  private static final PeepholePattern[] peepholePatterns =
      new PeepholePattern[] {new MunchAppends(), new MunchToString(), new MunchNonMaterializing()};

  static boolean optimize(
      StringBuilderNode root, StringBuilderNode currentNode, MunchingState munchingState) {
    if (currentNode.isDead()) {
      return false;
    }
    for (PeepholePattern peepholePattern : peepholePatterns) {
      if (peepholePattern.optimize(root, currentNode, munchingState)) {
        return true;
      }
    }
    return false;
  }
}
