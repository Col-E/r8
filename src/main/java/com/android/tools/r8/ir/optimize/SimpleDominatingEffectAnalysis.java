// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.ir.analysis.ClassInitializationAnalysis.Query.DIRECTLY;
import static com.android.tools.r8.ir.optimize.SimpleDominatingEffectAnalysis.InstructionEffect.NO_EFFECT;
import static com.android.tools.r8.ir.optimize.SimpleDominatingEffectAnalysis.InstructionEffect.OTHER_EFFECT;
import static com.android.tools.r8.utils.TraversalContinuation.doBreak;
import static com.android.tools.r8.utils.TraversalContinuation.doContinue;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis.AnalysisAssumption;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.DepthFirstSearchWorkListBase.DFSNodeWithState;
import com.android.tools.r8.utils.DepthFirstSearchWorkListBase.StatefulDepthFirstSearchWorkList;
import com.android.tools.r8.utils.IntBox;
import com.android.tools.r8.utils.TraversalContinuation;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class SimpleDominatingEffectAnalysis {

  public enum InstructionEffect {
    NO_EFFECT,
    DESIRED_EFFECT,
    OTHER_EFFECT;

    public boolean isDesired() {
      return this == DESIRED_EFFECT;
    }

    public boolean isOther() {
      return this == OTHER_EFFECT;
    }

    public boolean isNoEffect() {
      return this == NO_EFFECT;
    }

    public static InstructionEffect fromBoolean(boolean value) {
      return value ? DESIRED_EFFECT : OTHER_EFFECT;
    }

    public ResultState toResultState() {
      switch (this) {
        case NO_EFFECT:
          return ResultState.NOT_COMPUTED;
        case DESIRED_EFFECT:
          return ResultState.SATISFIED;
        default:
          assert isOther();
          return ResultState.NOT_SATISFIED;
      }
    }
  }

  /**
   * ResultState encodes a lattice on the following form: PARTIAL / \ SATISFIED NOT_SATISFIED \ /
   * NOT_COMPUTED
   *
   * <p>PARTIAL results occur when have control flow where one or more branches are SATISFIED and
   * one or more branches are NOT_SATISFIED.
   */
  private enum ResultState {
    PARTIAL,
    SATISFIED,
    NOT_SATISFIED,
    NOT_COMPUTED;

    public boolean isPartial() {
      return this == PARTIAL;
    }

    public boolean isSatisfied() {
      return this == SATISFIED;
    }

    public boolean isNotSatisfied() {
      return this == NOT_SATISFIED;
    }

    public boolean isNotComputed() {
      return this == NOT_COMPUTED;
    }

    public ResultState join(ResultState other) {
      if (isPartial() || other.isNotComputed()) {
        return this;
      }
      if (isNotComputed() || other.isPartial()) {
        return other;
      }
      if (this == other) {
        return this;
      }
      return PARTIAL;
    }
  }

  private static class ResultStateWithPartialBlocks {

    private final ResultState state;
    private final List<BasicBlock> failingBlocks;

    private ResultStateWithPartialBlocks(ResultState state, List<BasicBlock> failingBlocks) {
      this.state = state;
      this.failingBlocks = failingBlocks;
    }

    public ResultStateWithPartialBlocks joinChildren(
        List<DFSNodeWithState<BasicBlock, ResultStateWithPartialBlocks>> childNodes) {
      assert state.isNotComputed();
      ResultState newState =
          childNodes.isEmpty() ? ResultState.NOT_SATISFIED : ResultState.NOT_COMPUTED;
      for (DFSNodeWithState<BasicBlock, ResultStateWithPartialBlocks> childNode : childNodes) {
        ResultStateWithPartialBlocks childState = childNode.getState();
        assert !childState.state.isNotComputed();
        newState = newState.join(childState.state);
      }
      assert !newState.isNotComputed();
      List<BasicBlock> newFailingBlocks = new ArrayList<>();
      if (newState.isPartial()) {
        // Compute the initial basic blocks where that leads to OTHER_EFFECT.
        for (DFSNodeWithState<BasicBlock, ResultStateWithPartialBlocks> childNode : childNodes) {
          if (childNode.getState().state.isNotSatisfied()) {
            newFailingBlocks.add(childNode.getNode());
          } else if (childNode.getState().state.isPartial()) {
            newFailingBlocks.addAll(childNode.getState().failingBlocks);
          }
        }
      }
      return new ResultStateWithPartialBlocks(newState, newFailingBlocks);
    }
  }

  // The instruction analysis drives the SimpleDominatingEffectAnalysis by assigning an effect
  // to a basic block.
  public interface InstructionAnalysis {

    // Analyse the instruction and assign and effect. If the desired effect is seen, it should
    // dominate other users and DESIRED_EFFECT should be returned. If a violating effect is seen
    // return OTHER_EFFECT. When an effect is seen there is no need to visit successor instructions
    // in the block or successor blocks.
    // If the instruction do not violate the effect, use NO_EFFECT. The analysis will, if the entire
    // block is visited without any effect, visit successor blocks until an effect is found, the
    // depth is violated or we see a return for which the result of this path will be notSatisfied.
    InstructionEffect analyze(Instruction instruction);

    // Return the successors of the block. The default is to only look at normal successors.
    default List<BasicBlock> getSuccessors(BasicBlock block) {
      return block.getNormalSuccessors();
    }

    // The max bound on instructions to consider before giving up.
    default int maxNumberOfInstructions() {
      return 100;
    }
  }

  public static class SimpleEffectAnalysisResult {

    private final ResultState result;
    private final List<Instruction> satisfyingInstructions;
    private final List<BasicBlock> topmostNotSatisfiedBlocks;

    private SimpleEffectAnalysisResult(
        ResultState result,
        List<Instruction> satisfyingInstructions,
        List<BasicBlock> topmostNotSatisfiedBlocks) {
      this.result = result;
      this.satisfyingInstructions = satisfyingInstructions;
      this.topmostNotSatisfiedBlocks = topmostNotSatisfiedBlocks;
      assert !result.isPartial()
          || (!satisfyingInstructions.isEmpty() && !topmostNotSatisfiedBlocks.isEmpty());
    }

    public void forEachSatisfyingInstruction(Consumer<Instruction> instructionConsumer) {
      satisfyingInstructions.forEach(instructionConsumer);
    }

    public List<BasicBlock> getTopmostNotSatisfiedBlocks() {
      return topmostNotSatisfiedBlocks;
    }

    public static SimpleEffectAnalysisResultBuilder builder() {
      return new SimpleEffectAnalysisResultBuilder();
    }

    public boolean isNotSatisfied() {
      return result.isNotSatisfied();
    }

    public boolean isSatisfied() {
      return result.isSatisfied();
    }

    public boolean isPartial() {
      return result.isPartial();
    }
  }

  private static class SimpleEffectAnalysisResultBuilder {

    List<Instruction> satisfyingInstructions = new ArrayList<>();
    List<BasicBlock> failingBlocksForPartialResults = ImmutableList.of();
    private ResultState result;

    public void fail() {
      result = ResultState.NOT_SATISFIED;
    }

    public void addSatisfyingInstruction(Instruction instruction) {
      satisfyingInstructions.add(instruction);
    }

    public void setFailingBlocksForPartialResults(List<BasicBlock> basicBlocks) {
      this.failingBlocksForPartialResults = basicBlocks;
    }

    public void setResult(ResultState result) {
      this.result = result;
    }

    public SimpleEffectAnalysisResult build() {
      return result.isNotComputed()
          ? NO_RESULT
          : new SimpleEffectAnalysisResult(
              result, satisfyingInstructions, failingBlocksForPartialResults);
    }
  }

  private static final SimpleEffectAnalysisResult NO_RESULT =
      new SimpleEffectAnalysisResult(
          ResultState.NOT_SATISFIED, ImmutableList.of(), ImmutableList.of());

  public static SimpleEffectAnalysisResult run(IRCode code, InstructionAnalysis analysis) {
    SimpleEffectAnalysisResultBuilder builder = SimpleEffectAnalysisResult.builder();
    IntBox visitedInstructions = new IntBox();
    new StatefulDepthFirstSearchWorkList<BasicBlock, ResultStateWithPartialBlocks>() {

      @Override
      protected TraversalContinuation<?, ?> process(
          DFSNodeWithState<BasicBlock, ResultStateWithPartialBlocks> node,
          Function<BasicBlock, DFSNodeWithState<BasicBlock, ResultStateWithPartialBlocks>>
              childNodeConsumer) {
        InstructionEffect effect = NO_EFFECT;
        for (Instruction instruction : node.getNode().getInstructions()) {
          if (visitedInstructions.getAndIncrement() > analysis.maxNumberOfInstructions()) {
            builder.fail();
            return doBreak();
          }
          effect = analysis.analyze(instruction);
          if (!effect.isNoEffect()) {
            if (effect.isDesired()) {
              builder.addSatisfyingInstruction(instruction);
            }
            break;
          }
        }
        if (effect.isNoEffect()) {
          List<BasicBlock> successors = analysis.getSuccessors(node.getNode());
          for (BasicBlock successor : successors) {
            DFSNodeWithState<BasicBlock, ResultStateWithPartialBlocks> childNode =
                childNodeConsumer.apply(successor);
            if (childNode.hasState()) {
              // If we see a block where the children have not been processed we cannot guarantee
              // all paths having the effect since - ex. we could have a non-terminating loop.
              builder.fail();
              return doBreak();
            }
          }
        }
        node.setState(new ResultStateWithPartialBlocks(effect.toResultState(), ImmutableList.of()));
        return doContinue();
      }

      @Override
      protected TraversalContinuation<?, ?> joiner(
          DFSNodeWithState<BasicBlock, ResultStateWithPartialBlocks> node,
          List<DFSNodeWithState<BasicBlock, ResultStateWithPartialBlocks>> childNodes) {
        ResultStateWithPartialBlocks resultState = node.getState();
        if (resultState.state.isNotComputed()) {
          resultState = resultState.joinChildren(childNodes);
        } else {
          assert resultState.state.isSatisfied() || resultState.state.isNotSatisfied();
          assert childNodes.isEmpty();
        }
        node.setState(resultState);
        if (node.getNode().isEntry()) {
          builder.setResult(resultState.state);
          builder.setFailingBlocksForPartialResults(resultState.failingBlocks);
        }
        return doContinue();
      }
    }.run(code.entryBlock());

    return builder.build();
  }

  public static SimpleEffectAnalysisResult canInlineWithoutSynthesizingNullCheckForReceiver(
      AppView<?> appView, IRCode code) {
    assert code.context().getDefinition().isVirtualMethod();
    Value receiver = code.getThis();
    if (!receiver.isUsed()) {
      return NO_RESULT;
    }
    ProgramMethod context = code.context();
    return run(
        code,
        instruction -> {
          if ((instruction.isInvokeMethodWithReceiver()
                  && instruction.asInvokeMethodWithReceiver().getReceiver() == receiver)
              || (instruction.isInstanceFieldInstruction()
                  && instruction.asInstanceFieldInstruction().object() == receiver)
              || (instruction.isMonitorEnter() && instruction.asMonitor().object() == receiver)) {
            // Conservatively bailout if there are catch handlers.
            return InstructionEffect.fromBoolean(!instruction.getBlock().hasCatchHandlers());
          }
          return instruction.instructionMayHaveSideEffects(appView, context)
              ? OTHER_EFFECT
              : NO_EFFECT;
        });
  }

  public static SimpleEffectAnalysisResult triggersClassInitializationBeforeAnyStaticRead(
      AppView<AppInfoWithLiveness> appView, IRCode code, ProgramMethod context) {
    assert code.context().getDefinition().isStatic();
    return run(
        code,
        instruction -> {
          if (instruction.definitelyTriggersClassInitialization(
              code.context().getHolderType(),
              context,
              appView,
              DIRECTLY,
              AnalysisAssumption.INSTRUCTION_DOES_NOT_THROW)) {
            // In order to preserve class initialization semantic, the exception must not be caught
            // by any handler. Therefore, we must ignore this instruction if it is covered by a
            // catch handler.
            // Note: this is a conservative approach where we consider that any catch handler could
            // catch the exception, even if it cannot catch an ExceptionInInitializerError.
            return InstructionEffect.fromBoolean(!instruction.getBlock().hasCatchHandlers());
          }
          // A static field can be updated by a static initializer and then accessed by an instance
          // method. This is a problem if we later see DESIRED_EFFECT. The check for any instance
          // method is quite conservative.
          // TODO(b/217530538): Track if instance methods is accessing static fields.
          return instruction.isInvokeMethodWithReceiver()
                  || instruction.instructionMayHaveSideEffects(appView, context)
              ? OTHER_EFFECT
              : NO_EFFECT;
        });
  }
}
