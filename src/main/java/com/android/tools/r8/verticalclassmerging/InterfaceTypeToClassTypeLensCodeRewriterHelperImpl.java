// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.verticalclassmerging;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens.MethodLookupResult;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.CheckCast;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.OptionalBool;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;

// TODO(b/199561570): Extend this to insert casts for users that are not an instance of
//  invoke-method (e.g., array-put, instance-put, static-put, return).
public class InterfaceTypeToClassTypeLensCodeRewriterHelperImpl
    extends InterfaceTypeToClassTypeLensCodeRewriterHelper {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final IRCode code;

  private final Map<Instruction, Deque<WorklistItem>> worklist = new IdentityHashMap<>();

  public InterfaceTypeToClassTypeLensCodeRewriterHelperImpl(
      AppView<? extends AppInfoWithClassHierarchy> appView, IRCode code) {
    this.appView = appView;
    this.code = code;
  }

  @Override
  public void insertCastsForOperandsIfNeeded(
      InvokeMethod originalInvoke,
      InvokeMethod rewrittenInvoke,
      MethodLookupResult lookupResult,
      BasicBlockIterator blockIterator,
      BasicBlock block,
      InstructionListIterator instructionIterator) {
    DexMethod originalInvokedMethod = originalInvoke.getInvokedMethod();
    DexMethod rewrittenInvokedMethod = rewrittenInvoke.getInvokedMethod();
    if (lookupResult.getPrototypeChanges().getArgumentInfoCollection().hasRemovedArguments()) {
      // There is no argument removal before the primary optimization pass.
      assert false;
      return;
    }

    // Intentionally iterate the arguments of the original invoke, since the rewritten invoke could
    // have extra arguments added.
    for (int operandIndex = 0; operandIndex < originalInvoke.arguments().size(); operandIndex++) {
      Value operand = rewrittenInvoke.getArgument(operandIndex);
      DexType originalType =
          originalInvokedMethod.getArgumentType(operandIndex, originalInvoke.isInvokeStatic());
      DexType rewrittenType =
          rewrittenInvokedMethod.getArgumentType(operandIndex, rewrittenInvoke.isInvokeStatic());
      if (needsCastForOperand(operand, block, originalType, rewrittenType).isPossiblyTrue()) {
        worklist
            .computeIfAbsent(rewrittenInvoke, ignoreKey(ArrayDeque::new))
            .addLast(new WorklistItem(operandIndex, originalType, rewrittenType));
      }
    }
  }

  @Override
  public void processWorklist() {
    if (worklist.isEmpty()) {
      return;
    }

    BasicBlockIterator blockIterator = code.listIterator();
    boolean isCodeFullyRewrittenWithLens = true;
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      InstructionListIterator instructionIterator = block.listIterator(code);
      while (instructionIterator.hasNext()) {
        Instruction instruction = instructionIterator.next();
        Deque<WorklistItem> worklistItems = worklist.get(instruction);
        if (worklistItems == null) {
          continue;
        }
        for (WorklistItem worklistItem : worklistItems) {
          Value operand = instruction.getOperand(worklistItem.operandIndex);
          DexType originalType = worklistItem.originalType;
          DexType rewrittenType = worklistItem.rewrittenType;
          OptionalBool needsCastForOperand =
              needsCastForOperand(
                  operand, block, originalType, rewrittenType, isCodeFullyRewrittenWithLens);
          assert !needsCastForOperand.isUnknown();
          if (needsCastForOperand.isTrue()) {
            insertCastForOperand(
                operand, rewrittenType, instruction, blockIterator, block, instructionIterator);
          }
        }
      }
    }
  }

  private void insertCastForOperand(
      Value operand,
      DexType castType,
      Instruction rewrittenUser,
      BasicBlockIterator blockIterator,
      BasicBlock block,
      InstructionListIterator instructionIterator) {
    Instruction previous = instructionIterator.previous();
    assert previous == rewrittenUser;

    CheckCast checkCast =
        CheckCast.builder()
            .setCastType(castType)
            .setObject(operand)
            .setFreshOutValue(code, castType.toTypeElement(appView), operand.getLocalInfo())
            .setPosition(rewrittenUser)
            .build();
    if (block.hasCatchHandlers()) {
      instructionIterator
          .splitCopyCatchHandlers(code, blockIterator, appView.options())
          .listIterator(code)
          .add(checkCast);
    } else {
      instructionIterator.add(checkCast);
    }
    rewrittenUser.replaceValue(operand, checkCast.outValue());

    Instruction next = instructionIterator.next();
    assert next == rewrittenUser;
  }

  private boolean isOperandRewrittenWithLens(
      Value operand, BasicBlock blockWithUser, boolean isCodeFullyRewrittenWithLens) {
    if (isCodeFullyRewrittenWithLens) {
      return true;
    }
    if (operand.isPhi()) {
      return false;
    }
    Instruction definition = operand.getDefinition();
    return definition.isArgument() || operand.getBlock() == blockWithUser;
  }

  private OptionalBool needsCastForOperand(
      Value operand, BasicBlock blockWithUser, DexType originalType, DexType rewrittenType) {
    return needsCastForOperand(operand, blockWithUser, originalType, rewrittenType, false);
  }

  private OptionalBool needsCastForOperand(
      Value operand,
      BasicBlock blockWithUser,
      DexType originalType,
      DexType rewrittenType,
      boolean isCodeFullyRewrittenWithLens) {
    if (!originalType.isClassType() || !rewrittenType.isClassType()) {
      return OptionalBool.FALSE;
    }
    // The original type should be an interface type.
    DexProgramClass originalClass = asProgramClassOrNull(appView.definitionFor(originalType));
    if (originalClass == null || !originalClass.isInterface()) {
      return OptionalBool.FALSE;
    }
    // The rewritten type should be a (non-interface) class type.
    DexProgramClass rewrittenClass = asProgramClassOrNull(appView.definitionFor(rewrittenType));
    if (rewrittenClass == null || rewrittenClass.isInterface()) {
      return OptionalBool.FALSE;
    }
    // If the operand has not yet been rewritten with the lens, we delay the type check until
    // after lens code rewriting.
    if (!isOperandRewrittenWithLens(operand, blockWithUser, isCodeFullyRewrittenWithLens)) {
      assert !isCodeFullyRewrittenWithLens;
      return OptionalBool.UNKNOWN;
    }
    // The operand should not be subtype of the rewritten type.
    TypeElement rewrittenTypeElement = rewrittenType.toTypeElement(appView);
    return OptionalBool.of(
        !operand.getType().lessThanOrEqualUpToNullability(rewrittenTypeElement, appView));
  }

  private static class WorklistItem {

    final int operandIndex;
    final DexType originalType;
    final DexType rewrittenType;

    WorklistItem(int operandIndex, DexType originalType, DexType rewrittenType) {
      this.operandIndex = operandIndex;
      this.originalType = originalType;
      this.rewrittenType = rewrittenType;
    }
  }
}
