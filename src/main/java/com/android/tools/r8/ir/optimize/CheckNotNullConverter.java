// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;

public class CheckNotNullConverter {

  public static void runIfNecessary(AppView<?> appView, IRCode code) {
    if (appView.enableWholeProgramOptimizations()) {
      run(appView.withClassHierarchy(), code);
      assert code.isConsistentSSA(appView);
    }
  }

  /**
   * Replace all calls to methods marked as a check-not-null method by a call to Object.getClass(),
   * using the first argument as the receiver for the new call.
   *
   * <p>If the invoke has an out-value, the out-value is replaced by the first argument to allow
   * removing the invoke.
   */
  private static void run(AppView<? extends AppInfoWithClassHierarchy> appView, IRCode code) {
    BasicBlockIterator blockIterator = code.listIterator();
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      InstructionListIterator instructionIterator = block.listIterator(code);
      while (instructionIterator.hasNext()) {
        Instruction instruction = instructionIterator.next();
        if (instruction.isInvokeMethod()) {
          rewriteInvoke(appView, code, instructionIterator, instruction.asInvokeMethod());
        }
      }
    }
  }

  private static void rewriteInvoke(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke) {
    ProgramMethod context = code.context();
    DexClassAndMethod singleTarget = invoke.lookupSingleTarget(appView, context);
    if (singleTarget == null || !singleTarget.getOptimizationInfo().isConvertCheckNotNull()) {
      return;
    }
    Value checkNotNullValue = invoke.getFirstNonReceiverArgument();
    if (invoke.hasUsedOutValue()) {
      invoke.outValue().replaceUsers(checkNotNullValue);
    }
    if (checkNotNullValue.getType().nullability().isDefinitelyNotNull()) {
      instructionIterator.removeOrReplaceByDebugLocalRead();
    } else {
      instructionIterator.replaceCurrentInstructionWithNullCheck(appView, checkNotNullValue);
    }
  }
}
