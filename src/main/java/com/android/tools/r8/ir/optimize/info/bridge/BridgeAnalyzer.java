// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.info.bridge;

import static com.android.tools.r8.ir.code.Opcodes.ARGUMENT;
import static com.android.tools.r8.ir.code.Opcodes.ASSUME;
import static com.android.tools.r8.ir.code.Opcodes.CHECK_CAST;
import static com.android.tools.r8.ir.code.Opcodes.GOTO;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_DIRECT;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_VIRTUAL;
import static com.android.tools.r8.ir.code.Opcodes.RETURN;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.ir.code.AssumeAndCheckCastAliasedValueConfiguration;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.CheckCast;
import com.android.tools.r8.ir.code.Goto;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.BooleanUtils;

public class BridgeAnalyzer {

  /** Returns a {@link BridgeInfo} object describing this method if it is recognized as a bridge. */
  public static BridgeInfo analyzeMethod(DexEncodedMethod method, IRCode code) {
    // Scan through the instructions one-by-one. We expect a sequence of Argument instructions,
    // followed by a (possibly empty) sequence of CheckCast instructions, followed by a single
    // InvokeMethod instruction, followed by an optional CheckCast instruction, followed by a Return
    // instruction.
    InvokeMethodWithReceiver uniqueInvoke = null;
    CheckCast uniqueReturnCast = null;
    InstructionListIterator instructionIterator = code.entryBlock().listIterator(code);
    while (instructionIterator.hasNext()) {
      Instruction instruction = instructionIterator.next();
      switch (instruction.opcode()) {
        case ARGUMENT:
          break;

        case ASSUME:
          break;

        case CHECK_CAST:
          {
            CheckCast checkCast = instruction.asCheckCast();
            if (!analyzeCheckCast(method, checkCast, uniqueInvoke)) {
              return failure();
            }
            // If we have moved past the single invoke instruction, then record that this cast is
            // the cast instruction for the result value.
            if (uniqueInvoke != null) {
              if (uniqueReturnCast != null) {
                return failure();
              }
              uniqueReturnCast = checkCast;
            }
            break;
          }

        case INVOKE_DIRECT:
        case INVOKE_VIRTUAL:
          {
            if (uniqueInvoke != null) {
              return failure();
            }
            InvokeMethodWithReceiver invoke = instruction.asInvokeMethodWithReceiver();
            if (!analyzeInvoke(invoke)) {
              return failure();
            }
            // Record that we have seen the single invoke instruction.
            uniqueInvoke = invoke;
            break;
          }

        case GOTO:
          {
            Goto gotoInstruction = instruction.asGoto();
            BasicBlock targetBlock = gotoInstruction.getTarget();
            if (targetBlock.hasCatchHandlers()) {
              return failure();
            }
            instructionIterator = targetBlock.listIterator(code);
            break;
          }

        case RETURN:
          if (!analyzeReturn(instruction.asReturn(), uniqueInvoke, uniqueReturnCast)) {
            return failure();
          }
          break;

        default:
          return failure();
      }
    }

    assert uniqueInvoke != null;
    assert uniqueInvoke.isInvokeDirect() || uniqueInvoke.isInvokeVirtual();
    return uniqueInvoke.isInvokeDirect()
        ? new DirectBridgeInfo(uniqueInvoke.getInvokedMethod())
        : new VirtualBridgeInfo(uniqueInvoke.getInvokedMethod());
  }

  private static boolean analyzeCheckCast(
      DexEncodedMethod method, CheckCast checkCast, InvokeMethod invoke) {
    return invoke == null
        ? analyzeCheckCastBeforeInvoke(checkCast)
        : analyzeCheckCastAfterInvoke(method, checkCast, invoke);
  }

  private static boolean analyzeCheckCastBeforeInvoke(CheckCast checkCast) {
    Value object = checkCast.object().getAliasedValue();
    // It must be casting one of the arguments.
    if (!object.isArgument()) {
      return false;
    }
    int argumentIndex = object.definition.asArgument().getIndex();
    Value castValue = checkCast.outValue();
    // The out value should not have any phi users since we only allow linear control flow.
    if (castValue.hasPhiUsers()) {
      return false;
    }
    // It is not allowed to have any other users than the invoke instruction.
    if (castValue.hasDebugUsers() || !castValue.hasSingleUniqueUser()) {
      return false;
    }
    InvokeMethod invoke = castValue.singleUniqueUser().asInvokeMethod();
    if (invoke == null) {
      return false;
    }
    if (invoke.arguments().size() <= argumentIndex) {
      return false;
    }
    int parameterIndex = argumentIndex - BooleanUtils.intValue(invoke.isInvokeMethodWithReceiver());
    // It is not allowed to cast the receiver.
    if (parameterIndex == -1) {
      return false;
    }
    // The type of the cast must match the type of the parameter.
    if (checkCast.getType() != invoke.getInvokedMethod().proto.getParameter(parameterIndex)) {
      return false;
    }
    // It must be forwarded at the same argument index.
    Value argument = invoke.getArgument(argumentIndex);
    if (argument != castValue) {
      return false;
    }
    return true;
  }

  private static boolean analyzeCheckCastAfterInvoke(
      DexEncodedMethod method, CheckCast checkCast, InvokeMethod invoke) {
    Value returnValue = invoke.outValue();
    Value uncastValue = checkCast.object().getAliasedValue();
    Value castValue = checkCast.outValue();
    // The out value should not have any phi users since we only allow linear control flow.
    if (castValue.hasPhiUsers()) {
      return false;
    }
    // It must cast the result to the return type of the enclosing method and return the cast value.
    return uncastValue == returnValue
        && checkCast.getType() == method.returnType()
        && !castValue.hasDebugUsers()
        && castValue.hasSingleUniqueUser()
        && castValue.singleUniqueUser().isReturn();
  }

  private static boolean analyzeInvoke(InvokeMethodWithReceiver invoke) {
    // All of the forwarded arguments of the enclosing method must be in the same argument position.
    for (int argumentIndex = 0; argumentIndex < invoke.arguments().size(); argumentIndex++) {
      Value argument = invoke.getArgument(argumentIndex).getAliasedValue();
      if (argument.isPhi()
          || (argument.isArgument()
              && argumentIndex != argument.getDefinition().asArgument().getIndex())) {
        return false;
      } else {
        // Validate that besides argument values only check-cast of argument values are allowed at
        // their argument position.
        assert argument.isArgument()
            || (argument.getDefinition().isCheckCast()
                && invoke
                    .getArgument(argumentIndex)
                    .getAliasedValue(AssumeAndCheckCastAliasedValueConfiguration.getInstance())
                    .isArgument()
                && invoke
                        .getArgument(argumentIndex)
                        .getAliasedValue(AssumeAndCheckCastAliasedValueConfiguration.getInstance())
                        .getDefinition()
                        .asArgument()
                        .getIndex()
                    == argumentIndex);
      }
    }
    return true;
  }

  private static boolean analyzeReturn(Return ret, InvokeMethod invoke, CheckCast returnCast) {
    // If we haven't seen an invoke this is not a bridge.
    if (invoke == null) {
      return false;
    }
    // It must not return a value, or the return value must be the result value of the invoke.
    return ret.isReturnVoid()
        || ret.returnValue().getAliasedValue()
            == (returnCast != null ? returnCast : invoke).outValue();
  }

  private static BridgeInfo failure() {
    return null;
  }
}
