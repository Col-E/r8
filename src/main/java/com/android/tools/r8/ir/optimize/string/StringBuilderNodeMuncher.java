// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.string;

import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.string.StringBuilderAction.AppendWithNewConstantString;
import com.android.tools.r8.ir.optimize.string.StringBuilderAction.RemoveStringBuilderAction;
import com.android.tools.r8.ir.optimize.string.StringBuilderAction.ReplaceArgumentByExistingString;
import com.android.tools.r8.ir.optimize.string.StringBuilderAction.ReplaceArgumentByStringConcat;
import com.android.tools.r8.ir.optimize.string.StringBuilderAction.ReplaceByConstantString;
import com.android.tools.r8.ir.optimize.string.StringBuilderAction.ReplaceByExistingString;
import com.android.tools.r8.ir.optimize.string.StringBuilderAction.ReplaceByStringConcat;
import com.android.tools.r8.ir.optimize.string.StringBuilderNode.AppendNode;
import com.android.tools.r8.ir.optimize.string.StringBuilderNode.ImplicitToStringNode;
import com.android.tools.r8.ir.optimize.string.StringBuilderNode.InitNode;
import com.android.tools.r8.ir.optimize.string.StringBuilderNode.InitOrAppendNode;
import com.android.tools.r8.ir.optimize.string.StringBuilderNode.NewInstanceNode;
import com.android.tools.r8.ir.optimize.string.StringBuilderNode.StringBuilderInstruction;
import com.android.tools.r8.ir.optimize.string.StringBuilderNode.ToStringNode;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

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
    private final Map<StringBuilderNode, NewInstanceNode> newInstances;
    private final Map<Value, String> optimizedStrings = new IdentityHashMap<>();
    private final Supplier<Value> newValueSupplier;

    MunchingState(
        Map<Instruction, StringBuilderAction> actions,
        Set<StringBuilderNode> escaping,
        Set<StringBuilderNode> inspectingCapacity,
        Set<StringBuilderNode> looping,
        Map<StringBuilderNode, Set<StringBuilderNode>> materializingInstructions,
        Map<StringBuilderNode, NewInstanceNode> newInstances,
        StringBuilderOracle oracle,
        Supplier<Value> newValueSupplier) {
      this.actions = actions;
      this.escaping = escaping;
      this.inspectingCapacity = inspectingCapacity;
      this.looping = looping;
      this.materializingInstructions = materializingInstructions;
      this.newInstances = newInstances;
      this.oracle = oracle;
      this.newValueSupplier = newValueSupplier;
    }

    public NewInstanceNode getNewInstanceNode(StringBuilderNode root) {
      return newInstances.get(root);
    }

    public boolean isLooping(StringBuilderNode root) {
      return looping.contains(root);
    }

    public boolean isEscaping(StringBuilderNode root) {
      return escaping.contains(root);
    }

    public boolean isInspecting(StringBuilderNode root) {
      return inspectingCapacity.contains(root);
    }

    public Value getNewOutValue() {
      return newValueSupplier.get();
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
      AppendNode appendNode = currentNode.asAppendNode();
      if (appendNode == null || !appendNode.hasSinglePredecessor()) {
        return false;
      }
      InitOrAppendNode previous = currentNode.getSinglePredecessor().asInitOrAppend();
      if (previous == null || !previous.hasSingleSuccessor()) {
        return false;
      }
      // The capacity changes based on the init call (on JVM it adds 16 to length of input).
      if (previous.isInitNode() && munchingState.inspectingCapacity.contains(root)) {
        return false;
      }
      String currentConstantArgument = getConstantArgumentForNode(appendNode, munchingState);
      if (currentConstantArgument == null) {
        return false;
      }
      String previousConstantArgument = getConstantArgumentForNode(previous, munchingState);
      if (previousConstantArgument == null) {
        return false;
      }
      String newConstant = previousConstantArgument + currentConstantArgument;
      previous.setConstantArgument(newConstant);
      munchingState.actions.put(
          previous.getInstruction(), new AppendWithNewConstantString(newConstant));
      munchingState.actions.put(
          appendNode.getInstruction(), RemoveStringBuilderAction.getInstance());
      currentNode.removeNode();
      return true;
    }
  }

  /**
   * This peephole will try to remove toString nodes and replace by a constant string:
   *
   * <pre>
   * newInstance -> init("foo") -> toString() => newInstance -> init("foo") -> append("bar")
   * actions: [toString() => ReplaceByConstantString("foo")]
   * </pre>
   *
   * <p>If the node is an implicitToString, we update the append of another builder to have the new
   * constant value directly. If not, we keep track of the outValue toString() being replaced by a
   * constant by updating {@code MunchingState.optimizedStrings}
   */
  private static class MunchToString implements PeepholePattern {

    @Override
    public boolean optimize(
        StringBuilderNode originalRoot,
        StringBuilderNode currentNode,
        MunchingState munchingState) {
      if (munchingState.isEscaping(originalRoot) || munchingState.isInspecting(originalRoot)) {
        return false;
      }
      if (!currentNode.isToStringNode() && !currentNode.isImplicitToStringNode()) {
        return false;
      }
      NewInstanceNode newInstanceNode = munchingState.getNewInstanceNode(originalRoot);
      if (newInstanceNode == null || !newInstanceNode.hasSingleSuccessor()) {
        return false;
      }
      InitNode init = newInstanceNode.getSingleSuccessor().asInitNode();
      if (init == null || !init.hasSingleSuccessor()) {
        return false;
      }
      if (!currentNode.hasSinglePredecessor() || currentNode.getSinglePredecessor() != init) {
        return false;
      }
      String initConstantArgument = getConstantArgumentForNode(init, munchingState);
      if (initConstantArgument == null) {
        return false;
      }
      // If the string builder dependency is directly given to another string builder, there is
      // no toString() but an append with this string builder as argument.
      if (currentNode.isToStringNode()) {
        ToStringNode toStringNode = currentNode.asToStringNode();
        munchingState.actions.put(
            toStringNode.getInstruction(), new ReplaceByConstantString(initConstantArgument));
        String oldValue =
            munchingState.optimizedStrings.put(
                toStringNode.getInstruction().outValue(), initConstantArgument);
        assert oldValue == null;
      } else {
        assert currentNode.isImplicitToStringNode();
        ImplicitToStringNode implicitToStringNode = currentNode.asImplicitToStringNode();
        InitOrAppendNode initOrAppend = implicitToStringNode.getInitOrAppend();
        initOrAppend.setConstantArgument(initConstantArgument);
        munchingState.actions.put(
            initOrAppend.getInstruction(), new AppendWithNewConstantString(initConstantArgument));
      }
      munchingState.materializingInstructions.get(originalRoot).remove(currentNode);
      currentNode.removeNode();
      return true;
    }
  }

  /**
   * This peephole will try to remove toString nodes and replace by an invoke to String.concat:
   *
   * <pre>
   * newInstance -> init(notNull(string)) -> append(notNull(otherString)) -> toString() =>
   * newInstance -> init(notNull(string)) -> append(otherString)
   * actions: [toString() => string.concat(otherString)]
   * </pre>
   *
   * <p>This pattern only triggers when a constant munching of toString could happen.
   */
  private static class MunchToStringIntoStringConcat implements PeepholePattern {

    @Override
    public boolean optimize(
        StringBuilderNode originalRoot,
        StringBuilderNode currentNode,
        MunchingState munchingState) {
      if (munchingState.isEscaping(originalRoot)
          || munchingState.isInspecting(originalRoot)
          || !currentNode.hasSinglePredecessor()) {
        return false;
      }
      if (!currentNode.isToStringNode() && !currentNode.isImplicitToStringNode()) {
        return false;
      }
      NewInstanceNode newInstanceNode = munchingState.getNewInstanceNode(originalRoot);
      if (newInstanceNode == null || !newInstanceNode.hasSingleSuccessor()) {
        return false;
      }
      InitOrAppendNode firstNode = newInstanceNode.getSingleSuccessor().asInitNode();
      if (firstNode == null || !firstNode.hasSingleSuccessor()) {
        return false;
      }
      if (firstNode.asInitNode().isConstructorInvokeSideEffectFree(munchingState.oracle)
          && "".equals(firstNode.getConstantArgument())
          && firstNode.hasSingleSuccessor()) {
        firstNode = firstNode.getSingleSuccessor().asAppendNode();
        if (firstNode == null
            || !firstNode.hasSinglePredecessor()
            || !firstNode.hasSingleSuccessor()) {
          return false;
        }
      }
      // We cannot String.concat or return the string safely when it is not constant and maybe null.
      if (!firstNode.hasConstantOrNonConstantArgument()) {
        return false;
      }
      List<InitOrAppendNode> initOrAppends = Lists.newArrayList(firstNode);
      if (currentNode.getSinglePredecessor() != firstNode) {
        AppendNode appendAfterFirstNode = firstNode.getSingleSuccessor().asAppendNode();
        AppendNode appendBeforeToString = currentNode.getSinglePredecessor().asAppendNode();
        if (appendAfterFirstNode == null
            || appendAfterFirstNode != appendBeforeToString
            || !appendAfterFirstNode.hasConstantOrNonConstantArgument()) {
          return false;
        }
        initOrAppends.add(appendAfterFirstNode);
      }
      // Check that all values are not constant otherwise we can compute the constant value and
      // replace all entirely.
      if (Iterables.all(initOrAppends, InitOrAppendNode::hasConstantArgument)) {
        return false;
      }
      InitOrAppendNode first = initOrAppends.get(0);
      // If the string builder dependency is directly given to another string builder, there is
      // no toString() but an append with this string builder as argument.
      if (currentNode.isToStringNode()) {
        Instruction currentInstruction = currentNode.asToStringNode().getInstruction();
        if (initOrAppends.size() == 1) {
          // Replace with the string itself.
          munchingState.actions.put(
              currentInstruction, new ReplaceByExistingString(first.getNonConstantArgument()));
        } else {
          InitOrAppendNode second = initOrAppends.get(1);
          ReplaceByStringConcat concatAction;
          if (first.hasConstantArgument()) {
            concatAction =
                ReplaceByStringConcat.replaceByNewConstantConcatValue(
                    first.getConstantArgument(), second.getNonConstantArgument());
          } else if (second.hasConstantArgument()) {
            concatAction =
                ReplaceByStringConcat.replaceByValueConcatNewConstant(
                    first.getNonConstantArgument(), second.getConstantArgument());
          } else {
            concatAction =
                ReplaceByStringConcat.replaceByValues(
                    first.getNonConstantArgument(), second.getNonConstantArgument());
          }
          munchingState.actions.put(currentInstruction, concatAction);
        }
      } else {
        assert currentNode.isImplicitToStringNode();
        ImplicitToStringNode implicitToStringNode = currentNode.asImplicitToStringNode();
        InitOrAppendNode initOrAppend = implicitToStringNode.getInitOrAppend();
        if (initOrAppends.size() == 1) {
          initOrAppend.setNonConstantArgument(first.getNonConstantArgument());
          munchingState.actions.put(
              initOrAppend.getInstruction(),
              new ReplaceArgumentByExistingString(first.getNonConstantArgument()));
        } else {
          // Changing append to String.concat require us to calculate a new string value that will
          // be the result. We allocate it here such that we can use it repeatedly in munching.
          InitOrAppendNode second = initOrAppends.get(1);
          Value newOutValue = munchingState.getNewOutValue();
          initOrAppend.setNonConstantArgument(newOutValue);
          ReplaceArgumentByStringConcat concatAction;
          if (first.hasConstantArgument()) {
            concatAction =
                ReplaceArgumentByStringConcat.replaceByNewConstantConcatValue(
                    first.getConstantArgument(), second.getNonConstantArgument(), newOutValue);
          } else if (second.hasConstantArgument()) {
            concatAction =
                ReplaceArgumentByStringConcat.replaceByValueConcatNewConstant(
                    first.getNonConstantArgument(), second.getConstantArgument(), newOutValue);
          } else {
            concatAction =
                ReplaceArgumentByStringConcat.replaceByValues(
                    first.getNonConstantArgument(), second.getNonConstantArgument(), newOutValue);
          }
          munchingState.actions.put(initOrAppend.getInstruction(), concatAction);
        }
      }
      munchingState.materializingInstructions.get(originalRoot).remove(currentNode);
      currentNode.removeNode();
      return true;
    }
  }

  private static String getConstantArgumentForNode(
      InitOrAppendNode node, MunchingState munchingState) {
    if (node.hasConstantArgument()) {
      return node.getConstantArgument();
    }
    return getOptimizedConstantArgument(node, munchingState);
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
        if (currentNode.isSplitReferenceNode() && !munchingState.isLooping(root)) {
          removeNode = currentNode.getSuccessors().isEmpty() || currentNode.hasSinglePredecessor();
        } else if (currentNode.isAppendNode() && !isEscaping) {
          AppendNode appendNode = currentNode.asAppendNode();
          boolean canRemoveIfNoInspectionOrMaterializing =
              !munchingState.inspectingCapacity.contains(root)
                  && munchingState.materializingInstructions.get(root).isEmpty();
          boolean canRemoveIfLastAndNoLoop =
              !isLoopingOnPath(root, currentNode, munchingState)
                  && currentNode.getSuccessors().isEmpty();
          boolean hasKnownArgumentOrCannotBeObserved =
              appendNode.hasConstantOrNonConstantArgument()
                  || !munchingState.oracle.canObserveStringBuilderCall(
                      currentNode.asAppendNode().getInstruction());
          if (canRemoveIfNoInspectionOrMaterializing
              && canRemoveIfLastAndNoLoop
              && hasKnownArgumentOrCannotBeObserved) {
            removeNode = true;
          }
        } else if (currentNode.isInitNode()
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
            Instruction currentInstruction =
                currentNode.asStringBuilderInstructionNode().getInstruction();
            StringBuilderAction currentAction = munchingState.actions.get(currentInstruction);
            if (currentAction != null
                && !currentAction.isAllowedToBeOverwrittenByRemoveStringBuilderAction()) {
              assert currentAction.isReplaceArgumentByStringConcat();
              currentAction.asReplaceArgumentByStringConcat().setRemoveInstruction();
            } else {
              munchingState.actions.put(
                  currentInstruction, RemoveStringBuilderAction.getInstance());
            }
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
      new PeepholePattern[] {
        new MunchAppends(),
        new MunchToString(),
        new MunchToStringIntoStringConcat(),
        new MunchNonMaterializing()
      };

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
