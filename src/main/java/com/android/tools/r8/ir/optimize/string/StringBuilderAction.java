// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.string;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.Value;
import com.google.common.collect.ImmutableList;

/** StringBuilderAction defines an interface for updating the IR code based on optimizations. */
public interface StringBuilderAction {

  void perform(
      AppView<?> appView,
      IRCode code,
      InstructionListIterator iterator,
      Instruction instruction,
      StringBuilderOracle oracle);

  /** The RemoveStringBuilderAction will simply remove the instruction completely. */
  class RemoveStringBuilderAction implements StringBuilderAction {

    private static final RemoveStringBuilderAction INSTANCE = new RemoveStringBuilderAction();

    @Override
    public void perform(
        AppView<?> appView,
        IRCode code,
        InstructionListIterator iterator,
        Instruction instruction,
        StringBuilderOracle oracle) {
      assert oracle.isModeledStringBuilderInstruction(
          instruction,
          value ->
              value.getType().isClassType()
                  && oracle.isStringBuilderType(value.getType().asClassType().getClassType()));
      if (oracle.isAppend(instruction) && instruction.outValue() != null) {
        // Append will return the string builder instance. Before removing, ensure that
        // all users of the output values uses the receiver.
        instruction.outValue().replaceUsers(instruction.getFirstOperand());
      }
      iterator.removeOrReplaceByDebugLocalRead();
    }

    static RemoveStringBuilderAction getInstance() {
      return INSTANCE;
    }
  }

  /**
   * ReplaceByConstantString will replace a toString() call on StringBuilder with a constant string.
   */
  class ReplaceByConstantString implements StringBuilderAction {

    private final String replacement;

    ReplaceByConstantString(String replacement) {
      this.replacement = replacement;
    }

    @Override
    public void perform(
        AppView<?> appView,
        IRCode code,
        InstructionListIterator iterator,
        Instruction instruction,
        StringBuilderOracle oracle) {
      assert oracle.isToString(instruction, instruction.getFirstOperand());
      iterator.replaceCurrentInstructionWithConstString(appView, code, replacement);
    }
  }

  /**
   * AppendWithNewConstantString will change the current instruction to be an append with a constant
   * string. If the current instruction is init the instruction will be changed to an init taking a
   * string as argument.
   */
  class AppendWithNewConstantString implements StringBuilderAction {

    private final String replacement;

    AppendWithNewConstantString(String replacement) {
      this.replacement = replacement;
    }

    @Override
    public void perform(
        AppView<?> appView,
        IRCode code,
        InstructionListIterator iterator,
        Instruction instruction,
        StringBuilderOracle oracle) {
      Instruction previous = iterator.previous();
      InvokeMethodWithReceiver invoke = previous.asInvokeMethodWithReceiver();
      assert invoke != null;
      // If the block has catch handlers, inserting a constant string in the same block as the
      // append violates our block representation in DEX since constant string is throwing. If the
      // append is in a block with catch handlers, we simply insert a new constant string in the
      // entry block after all arguments.
      Value value;
      if (!invoke.getBlock().hasCatchHandlers()) {
        value =
            iterator.insertConstStringInstruction(
                appView, code, appView.dexItemFactory().createString(replacement));
      } else {
        InstructionListIterator stringInsertIterator = code.entryBlock().listIterator(code);
        while (stringInsertIterator.hasNext()) {
          Instruction next = stringInsertIterator.next();
          if (!next.isArgument()) {
            stringInsertIterator.previous();
            break;
          }
        }
        value =
            stringInsertIterator.insertConstStringInstruction(
                appView, code, appView.dexItemFactory().createString(replacement));
      }
      iterator.next();
      DexMethod invokedMethod = invoke.getInvokedMethod();
      if (invoke.isInvokeConstructor(appView.dexItemFactory())) {
        iterator.replaceCurrentInstruction(
            InvokeDirect.builder()
                .setArguments(ImmutableList.of(invoke.getReceiver(), value))
                .setMethod(
                    getConstructorWithStringParameter(invokedMethod, appView.dexItemFactory()))
                .setOutValue(invoke.outValue())
                .build());
      } else if (!isAppendWithString(invokedMethod, appView.dexItemFactory())) {
        iterator.replaceCurrentInstruction(
            InvokeVirtual.builder()
                .setArguments(ImmutableList.of(invoke.getReceiver(), value))
                .setMethod(getAppendWithStringParameter(invokedMethod, appView.dexItemFactory()))
                .setOutValue(invoke.outValue())
                .build());
      } else {
        invoke.replaceValue(1, value);
      }
    }

    private boolean isAppendWithString(DexMethod method, DexItemFactory factory) {
      return factory.stringBufferMethods.isAppendStringMethod(method)
          || factory.stringBuilderMethods.isAppendStringMethod(method);
    }

    private DexMethod getConstructorWithStringParameter(
        DexMethod invokedMethod, DexItemFactory factory) {
      if (invokedMethod.getHolderType() == factory.stringBufferType) {
        return factory.stringBufferMethods.stringConstructor;
      } else {
        assert invokedMethod.getHolderType() == factory.stringBuilderType;
        return factory.stringBuilderMethods.stringConstructor;
      }
    }

    private DexMethod getAppendWithStringParameter(
        DexMethod invokedMethod, DexItemFactory factory) {
      if (invokedMethod.getHolderType() == factory.stringBufferType) {
        return factory.stringBufferMethods.appendString;
      } else {
        assert invokedMethod.getHolderType() == factory.stringBuilderType;
        return factory.stringBuilderMethods.appendString;
      }
    }
  }
}
