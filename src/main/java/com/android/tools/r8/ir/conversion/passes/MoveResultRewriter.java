// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.optimize.AssumeRemover;
import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfo;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.ListIterator;
import java.util.Set;

public class MoveResultRewriter extends CodeRewriterPass<AppInfo> {

  public MoveResultRewriter(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getTimingId() {
    return "MoveResultRewriter";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code) {
    return options.isGeneratingDex() && code.metadata().mayHaveInvokeMethod();
  }

  // Replace result uses for methods where something is known about what is returned.
  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    AssumeRemover assumeRemover = new AssumeRemover(appView, code);
    boolean changed = false;
    boolean mayHaveRemovedTrivialPhi = false;
    Set<BasicBlock> blocksToBeRemoved = Sets.newIdentityHashSet();
    ListIterator<BasicBlock> blockIterator = code.listIterator();
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      if (blocksToBeRemoved.contains(block)) {
        continue;
      }

      InstructionListIterator iterator = block.listIterator(code);
      while (iterator.hasNext()) {
        InvokeMethod invoke = iterator.next().asInvokeMethod();
        if (invoke == null || !invoke.hasOutValue() || invoke.outValue().hasLocalInfo()) {
          continue;
        }

        // Check if the invoked method is known to return one of its arguments.
        DexClassAndMethod target = invoke.lookupSingleTarget(appView, code.context());
        if (target == null) {
          continue;
        }

        MethodOptimizationInfo optimizationInfo = target.getDefinition().getOptimizationInfo();
        if (!optimizationInfo.returnsArgument()) {
          continue;
        }

        int argumentIndex = optimizationInfo.getReturnedArgument();
        // Replace the out value of the invoke with the argument and ignore the out value.
        if (argumentIndex < 0 || !checkArgumentType(invoke, argumentIndex)) {
          continue;
        }

        Value argument = invoke.arguments().get(argumentIndex);
        Value outValue = invoke.outValue();
        assert outValue.verifyCompatible(argument.outType());

        // Make sure that we are only narrowing information here. Note, in cases where we cannot
        // find the definition of types, computing lessThanOrEqual will return false unless it is
        // object.
        if (!argument.getType().lessThanOrEqual(outValue.getType(), appView)) {
          continue;
        }

        Set<Value> affectedValues =
            argument.getType().equals(outValue.getType())
                ? Collections.emptySet()
                : outValue.affectedValues();

        assumeRemover.markAssumeDynamicTypeUsersForRemoval(outValue);
        mayHaveRemovedTrivialPhi |= outValue.numberOfPhiUsers() > 0;
        outValue.replaceUsers(argument);
        invoke.setOutValue(null);
        changed = true;

        if (!affectedValues.isEmpty()) {
          new TypeAnalysis(appView).narrowing(affectedValues);
        }
      }
    }
    assumeRemover.removeMarkedInstructions(blocksToBeRemoved).finish();
    Set<Value> affectedValues = Sets.newIdentityHashSet();
    if (!blocksToBeRemoved.isEmpty()) {
      code.removeBlocks(blocksToBeRemoved);
      code.removeAllDeadAndTrivialPhis(affectedValues);
      assert code.getUnreachableBlocks().isEmpty();
    } else if (mayHaveRemovedTrivialPhi || assumeRemover.mayHaveIntroducedTrivialPhi()) {
      code.removeAllDeadAndTrivialPhis(affectedValues);
    }
    if (!affectedValues.isEmpty()) {
      new TypeAnalysis(appView).narrowing(affectedValues);
    }
    assert code.isConsistentSSA(appView);
    return CodeRewriterResult.hasChanged(changed);
  }

  private boolean checkArgumentType(InvokeMethod invoke, int argumentIndex) {
    // TODO(sgjesse): Insert cast if required.
    TypeElement returnType =
        TypeElement.fromDexType(invoke.getInvokedMethod().proto.returnType, maybeNull(), appView);
    TypeElement argumentType =
        TypeElement.fromDexType(getArgumentType(invoke, argumentIndex), maybeNull(), appView);
    return appView.enableWholeProgramOptimizations()
        ? argumentType.lessThanOrEqual(returnType, appView)
        : argumentType.equals(returnType);
  }

  private DexType getArgumentType(InvokeMethod invoke, int argumentIndex) {
    if (invoke.isInvokeStatic()) {
      return invoke.getInvokedMethod().proto.parameters.values[argumentIndex];
    }
    if (argumentIndex == 0) {
      return invoke.getInvokedMethod().holder;
    }
    return invoke.getInvokedMethod().proto.parameters.values[argumentIndex - 1];
  }
}
