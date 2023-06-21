// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import static com.android.tools.r8.ir.code.Opcodes.ARGUMENT;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_GET;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_PUT;
import static com.android.tools.r8.ir.code.Opcodes.RETURN;

import com.android.tools.r8.dex.code.DexIget;
import com.android.tools.r8.dex.code.DexIput;
import com.android.tools.r8.dex.code.DexReturn;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.inliner.InliningIRProvider;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Analysis that estimates the cost of class inlining an object allocation. */
class ClassInlinerCostAnalysis {

  private final AppView<AppInfoWithLiveness> appView;
  private final InliningIRProvider inliningIRProvider;
  private final ClassInlinerReceiverSet receivers;

  private int estimatedCost = 0;

  ClassInlinerCostAnalysis(
      AppView<AppInfoWithLiveness> appView,
      InliningIRProvider inliningIRProvider,
      ClassInlinerReceiverSet receivers) {
    this.appView = appView;
    this.inliningIRProvider = inliningIRProvider;
    this.receivers = receivers;
  }

  boolean willExceedInstructionBudget(
      IRCode code,
      DexProgramClass eligibleClass,
      Map<InvokeMethod, ProgramMethod> directInlinees,
      List<ProgramMethod> indirectInlinees) {
    if (appView.appInfo().alwaysClassInline.contains(eligibleClass.type)) {
      return false;
    }

    for (ProgramMethod inlinee : indirectInlinees) {
      // We do not have the corresponding invoke instruction for the inlinees that are not called
      // directly from `code` (these are called indirectly from one of the methods in
      // `directInlinees`). Therefore, we currently choose not to build IR for estimating the number
      // of non-materializing instructions, since we cannot cache the IR (it would have the wrong
      // position).
      int increment = inlinee.getDefinition().getCode().estimatedSizeForInlining();
      if (exceedsInstructionBudgetAfterIncrement(increment)) {
        return true;
      }
    }

    // Visit the direct inlinees in a deterministic order to ensure that the state of the value
    // number generated is deterministic for each inlinee.
    int numberOfSeenDirectInlinees = 0;
    int numberOfDirectInlinees = directInlinees.size();
    for (InvokeMethod invoke : code.<InvokeMethod>instructions(Instruction::isInvokeMethod)) {
      ProgramMethod inlinee = directInlinees.get(invoke);
      if (inlinee == null) {
        // Not a direct inlinee.
        continue;
      }
      IRCode inliningIR = inliningIRProvider.getAndCacheInliningIR(invoke, inlinee);
      int increment =
          inlinee.getDefinition().getCode().estimatedSizeForInlining()
              - estimateSizeOfNonMaterializingInstructions(invoke, inliningIR);
      assert increment >= 0;
      if (exceedsInstructionBudgetAfterIncrement(increment)) {
        return true;
      }
      if (++numberOfSeenDirectInlinees == numberOfDirectInlinees) {
        break;
      }
    }

    // Verify that all direct inlinees have been visited.
    assert numberOfSeenDirectInlinees == numberOfDirectInlinees;

    return false;
  }

  private boolean exceedsInstructionBudgetAfterIncrement(int increment) {
    estimatedCost += increment;
    return estimatedCost
        > appView.options().classInlinerOptions().getClassInliningInstructionAllowance();
  }

  // TODO(b/143176500): Do not include instructions that will be canonicalized after inlining.
  //  Take care of that fact that after inlining the first method, this could introduce a constant
  //  in the caller, which could then lead to a constant in the second inlinee being canonicalized.
  // TODO(b/143176500): Do not include instructions that will be dead code eliminated as a result of
  //  constant arguments.
  private int estimateSizeOfNonMaterializingInstructions(InvokeMethod invoke, IRCode inlinee) {
    int result = 0;
    Set<Value> receiverAliasesInInlinee = null;
    for (Instruction instruction : inlinee.instructions()) {
      switch (instruction.opcode()) {
        case ARGUMENT:
          // Intentionally not counted as a non-materializing instruction, since there are no
          // argument instructions in the CF/DEX code.
          break;

        case INSTANCE_GET:
        case INSTANCE_PUT:
          // Will not materialize after class inlining if the object is the "root" instance.
          Value object =
              instruction.isInstanceGet()
                  ? instruction.asInstanceGet().object()
                  : instruction.asInstancePut().object();
          Value root = object.getAliasedValue();
          if (receiverAliasesInInlinee == null) {
            receiverAliasesInInlinee = getReceiverAliasesInInlinee(invoke, inlinee);
          }
          if (receiverAliasesInInlinee.contains(root)) {
            if (appView.options().isGeneratingClassFiles()) {
              result++;
            } else {
              result += instruction.isInstanceGet() ? DexIget.SIZE : DexIput.SIZE;
            }
          }
          break;

        case RETURN:
          // Will not materialize after class inlining.
          if (appView.options().isGeneratingClassFiles()) {
            result++;
          } else {
            result += DexReturn.SIZE;
          }
          break;

        default:
          // Will materialize.
          break;
      }
    }
    return result;
  }

  private Set<Value> getReceiverAliasesInInlinee(InvokeMethod invoke, IRCode inlinee) {
    List<Value> arguments = inlinee.collectArguments();
    Set<Value> receiverAliasesInInlinee = Sets.newIdentityHashSet();
    for (int i = 0; i < invoke.inValues().size(); i++) {
      Value inValue = invoke.inValues().get(i);
      if (receivers.isReceiverAlias(inValue)) {
        receiverAliasesInInlinee.add(arguments.get(i));
      } else {
        assert !receivers.isReceiverAlias(inValue.getAliasedValue());
      }
    }
    return receiverAliasesInInlinee;
  }
}
