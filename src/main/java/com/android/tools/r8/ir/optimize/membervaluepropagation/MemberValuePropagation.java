// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation;

import static com.android.tools.r8.ir.code.Opcodes.ARRAY_GET;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_GET;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_PUT;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_DIRECT;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_INTERFACE;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_STATIC;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_SUPER;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_VIRTUAL;
import static com.android.tools.r8.ir.code.Opcodes.STATIC_GET;
import static com.android.tools.r8.ir.code.Opcodes.STATIC_PUT;
import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.SingleValue;
import com.android.tools.r8.ir.code.ArrayGet;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.IRMetadata;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.android.tools.r8.ir.optimize.membervaluepropagation.assume.AssumeInfo;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.IteratorUtils;
import java.util.Set;
import java.util.function.Predicate;

public abstract class MemberValuePropagation<T extends AppInfo> {

  final AppView<T> appView;

  MemberValuePropagation(AppView<T> appView) {
    this.appView = appView;
  }

  /**
   * Replace invoke targets and field accesses with constant values where possible.
   *
   * <p>Also assigns value ranges to values where possible.
   */
  public void run(IRCode code) {
    IRMetadata metadata = code.metadata();
    if (!metadata.mayHaveFieldInstruction() && !metadata.mayHaveInvokeMethod()) {
      return;
    }
    AffectedValues affectedValues = new AffectedValues();
    run(code, code.listIterator(), affectedValues, alwaysTrue());
    affectedValues.narrowingWithAssumeRemoval(appView, code);
    code.removeRedundantBlocks();
    assert code.isConsistentSSA(appView);
  }

  public void run(
      IRCode code,
      BasicBlockIterator blockIterator,
      Set<Value> affectedValues,
      Predicate<BasicBlock> blockTester) {
    ProgramMethod context = code.context();
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      if (!blockTester.test(block)) {
        continue;
      }
      InstructionListIterator iterator = block.listIterator(code);
      while (iterator.hasNext()) {
        Instruction current = iterator.next();
        switch (current.opcode()) {
          case ARRAY_GET:
            rewriteArrayGet(code, affectedValues, blockIterator, iterator, current.asArrayGet());
            break;
          case INSTANCE_GET:
            rewriteInstanceGet(
                code, affectedValues, blockIterator, iterator, current.asInstanceGet());
            break;
          case INSTANCE_PUT:
            rewriteInstancePut(code, iterator, current.asInstancePut());
            break;
          case INVOKE_DIRECT:
          case INVOKE_INTERFACE:
          case INVOKE_STATIC:
          case INVOKE_SUPER:
          case INVOKE_VIRTUAL:
            iterator =
                rewriteInvokeMethod(
                    code,
                    context,
                    affectedValues,
                    blockIterator,
                    iterator,
                    current.asInvokeMethod());
            break;
          case STATIC_GET:
            rewriteStaticGet(code, affectedValues, blockIterator, iterator, current.asStaticGet());
            break;
          case STATIC_PUT:
            rewriteStaticPut(code, iterator, current.asStaticPut());
            break;
          default:
            break;
        }
      }
    }
  }

  abstract void rewriteArrayGet(
      IRCode code,
      Set<Value> affectedValues,
      BasicBlockIterator blocks,
      InstructionListIterator iterator,
      ArrayGet arrayGet);

  abstract InstructionListIterator rewriteInstanceGet(
      IRCode code,
      Set<Value> affectedValues,
      BasicBlockIterator blocks,
      InstructionListIterator iterator,
      InstanceGet current);

  abstract void rewriteInstancePut(
      IRCode code, InstructionListIterator iterator, InstancePut current);

  abstract InstructionListIterator rewriteInvokeMethod(
      IRCode code,
      ProgramMethod context,
      Set<Value> affectedValues,
      BasicBlockIterator blocks,
      InstructionListIterator iterator,
      InvokeMethod invoke);

  abstract InstructionListIterator rewriteStaticGet(
      IRCode code,
      Set<Value> affectedValues,
      BasicBlockIterator blocks,
      InstructionListIterator iterator,
      StaticGet current);

  abstract void rewriteStaticPut(IRCode code, InstructionListIterator iterator, StaticPut current);

  boolean applyAssumeInfo(
      IRCode code,
      Set<Value> affectedValues,
      BasicBlockIterator blocks,
      InstructionListIterator iterator,
      Instruction current,
      AssumeInfo assumeInfo) {
    // Remove if side effect free.
    if (assumeInfo.isSideEffectFree() && current.hasUnusedOutValue()) {
      iterator.removeOrReplaceByDebugLocalRead();
      return true;
    }

    // Set value range if any.
    if (current.hasOutValue()
        && current.getOutType().isPrimitiveType()
        && assumeInfo.getAssumeValue().isNumberFromIntervalValue()) {
      current.outValue().setValueRange(assumeInfo.getAssumeValue().asNumberFromIntervalValue());
    }

    // Insert replacement if any.
    Instruction[] replacementInstructions =
        createReplacementFromAssumeInfo(assumeInfo, code, current);
    if (replacementInstructions == null) {
      return false;
    }

    if (replacementInstructions.length > 1) {
      assert false;
      return false;
    }

    Instruction replacement = ArrayUtils.first(replacementInstructions);
    affectedValues.addAll(current.outValue().affectedValues());
    if (assumeInfo.isSideEffectFree()) {
      iterator.replaceCurrentInstruction(replacement);
      return true;
    }
    BasicBlock block = current.getBlock();
    if (current.hasOutValue()) {
      assert replacement.outValue() != null;
      current.outValue().replaceUsers(replacement.outValue());
    }
    if (current.isInstanceGet()) {
      iterator.replaceCurrentInstructionByNullCheckIfPossible(appView, code.context());
    } else if (current.isStaticGet()) {
      StaticGet staticGet = current.asStaticGet();
      iterator.removeOrReplaceCurrentInstructionByInitClassIfPossible(
          appView, code, staticGet.getField().holder);
    }
    if (block.hasCatchHandlers()) {
      BasicBlock splitBlock = iterator.split(code, blocks);
      splitBlock.listIterator(code).add(replacement);

      // Process the materialized value.
      blocks.previous();
      assert !iterator.hasNext();
      assert IteratorUtils.peekNext(blocks) == splitBlock;

      return true;
    } else {
      iterator.add(replacement);
    }

    // Process the materialized value.
    iterator.previous();
    assert iterator.peekNext() == replacement;

    return true;
  }

  private Instruction[] createReplacementFromAssumeInfo(
      AssumeInfo assumeInfo, IRCode code, Instruction instruction) {
    if (assumeInfo.getAssumeValue().isUnknown()) {
      return null;
    }

    AbstractValue assumeValue = assumeInfo.getAssumeValue();
    if (assumeValue.isSingleValue()) {
      SingleValue singleValue = assumeValue.asSingleValue();
      if (singleValue.isMaterializableInContext(appView, code.context())) {
        return singleValue.createMaterializingInstructions(appView, code, instruction);
      }
    }

    return null;
  }
}
