// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.string;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.AbstractState;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.DataflowAnalysisResult;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.FailedTransferFunctionResult;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.IntraproceduralDataflowAnalysis;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.TransferFunction;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.TransferFunctionResult;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.SetUtils;
import com.google.common.collect.Sets;
import java.util.Set;

/**
 * This defines a simple program analysis that determines if there is a path from a call to append()
 * on a StringBuilder back to itself in the control flow graph.
 *
 * <p>The analysis explicitly allows paths from a call to append() back to itself that go through a
 * call to toString() on the builder. This ensures that we can still optimize builders that are
 * fully enclosed in a loop: <code>
 *   while (true) {
 *     System.out.println(new StringBuilder().append("42").toString());
 *   }
 * </code>
 */
class StringBuilderAppendFlowAnalysis {

  /**
   * Returns true if there is a call to {@code append()} on {@param builder}, which is inside a
   * loop.
   */
  static boolean hasAppendInstructionInLoop(
      Value builder, StringBuilderOptimizationConfiguration configuration) {
    IntraproceduralDataflowAnalysis<AbstractStateImpl> analysis =
        new IntraproceduralDataflowAnalysis<>(
            AbstractStateImpl.bottom(), new TransferFunctionImpl(builder, configuration));
    DataflowAnalysisResult result = analysis.run(builder.definition.getBlock());
    return result.isFailedAnalysisResult();
  }

  /** This defines the state that we keep track of for each {@link BasicBlock}. */
  private static class AbstractStateImpl extends AbstractState<AbstractStateImpl> {

    private static final AbstractStateImpl BOTTOM = new AbstractStateImpl();

    // The set of invoke instructions that call append(), which is on a path to the current program
    // point.
    private final Set<InvokeVirtual> liveAppendInstructions;

    private AbstractStateImpl() {
      this(Sets.newIdentityHashSet());
    }

    private AbstractStateImpl(Set<InvokeVirtual> liveAppendInstructions) {
      this.liveAppendInstructions = liveAppendInstructions;
    }

    public static AbstractStateImpl bottom() {
      return BOTTOM;
    }

    private AbstractStateImpl addLiveAppendInstruction(InvokeVirtual invoke) {
      Set<InvokeVirtual> newLiveAppendInstructions =
          SetUtils.newIdentityHashSet(liveAppendInstructions);
      newLiveAppendInstructions.add(invoke);
      return new AbstractStateImpl(newLiveAppendInstructions);
    }

    private boolean isAppendInstructionLive(InvokeVirtual invoke) {
      return liveAppendInstructions.contains(invoke);
    }

    @Override
    public AbstractStateImpl asAbstractState() {
      return this;
    }

    @Override
    public AbstractStateImpl join(AbstractStateImpl state) {
      if (liveAppendInstructions.isEmpty()) {
        return state;
      }
      if (state.liveAppendInstructions.isEmpty()) {
        return this;
      }
      Set<InvokeVirtual> newLiveAppendInstructions =
          SetUtils.newIdentityHashSet(liveAppendInstructions, state.liveAppendInstructions);
      return new AbstractStateImpl(newLiveAppendInstructions);
    }

    @Override
    public boolean equals(Object other) {
      if (other == null || getClass() != other.getClass()) {
        return false;
      }
      AbstractStateImpl state = (AbstractStateImpl) other;
      return liveAppendInstructions.equals(state.liveAppendInstructions);
    }

    @Override
    public int hashCode() {
      return liveAppendInstructions.hashCode();
    }
  }

  /**
   * This defines the transfer function for the analysis.
   *
   * <p>If a call to {@code append()} on the builder is seen, then that invoke instruction is added
   * to the abstract state.
   *
   * <p>If a call to {@code toString()} on the builder i seen, then the abstract state is reset to
   * bottom.
   */
  private static class TransferFunctionImpl implements TransferFunction<AbstractStateImpl> {

    private final Value builder;
    private final StringBuilderOptimizationConfiguration configuration;

    private TransferFunctionImpl(
        Value builder, StringBuilderOptimizationConfiguration configuration) {
      this.builder = builder;
      this.configuration = configuration;
    }

    @Override
    public TransferFunctionResult<AbstractStateImpl> apply(
        Instruction instruction, AbstractStateImpl state) {
      if (instruction.isInvokeMethod()) {
        return apply(state, instruction.asInvokeMethod());
      }
      return state;
    }

    private TransferFunctionResult<AbstractStateImpl> apply(
        AbstractStateImpl state, InvokeMethod invoke) {
      if (isAppendOnBuilder(invoke)) {
        assert invoke.isInvokeVirtual();
        InvokeVirtual appendInvoke = invoke.asInvokeVirtual();
        if (state.isAppendInstructionLive(appendInvoke)) {
          return new FailedTransferFunctionResult<>();
        }
        return state.addLiveAppendInstruction(appendInvoke);
      }
      if (isToStringOnBuilder(invoke)) {
        return AbstractStateImpl.bottom();
      }
      return state;
    }

    private boolean isAppendOnBuilder(InvokeMethod invoke) {
      DexMethod invokedMethod = invoke.getInvokedMethod();
      return configuration.isAppendMethod(invokedMethod)
          && invoke.getArgument(0).getAliasedValue() == builder;
    }

    private boolean isToStringOnBuilder(InvokeMethod invoke) {
      DexMethod invokedMethod = invoke.getInvokedMethod();
      return configuration.isToStringMethod(invokedMethod)
          && invoke.getArgument(0).getAliasedValue() == builder;
    }
  }
}
