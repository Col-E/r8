// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.string;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.google.common.collect.ImmutableList;

/** StringBuilderAction defines an interface for updating the IR code based on optimizations. */
public interface StringBuilderAction {

  void perform(
      AppView<?> appView,
      IRCode code,
      InstructionListIterator iterator,
      Instruction instruction,
      AffectedValues affectedValues,
      StringBuilderOracle oracle);

  default boolean isAllowedToBeOverwrittenByRemoveStringBuilderAction() {
    return false;
  }

  default boolean isReplaceArgumentByStringConcat() {
    return false;
  }

  default ReplaceArgumentByStringConcat asReplaceArgumentByStringConcat() {
    return null;
  }

  /** The RemoveStringBuilderAction will simply remove the instruction completely. */
  class RemoveStringBuilderAction implements StringBuilderAction {

    private static final RemoveStringBuilderAction INSTANCE = new RemoveStringBuilderAction();

    @Override
    public void perform(
        AppView<?> appView,
        IRCode code,
        InstructionListIterator iterator,
        Instruction instruction,
        AffectedValues affectedValues,
        StringBuilderOracle oracle) {
      removeStringBuilderInstruction(iterator, instruction, affectedValues, oracle);
    }

    static RemoveStringBuilderAction getInstance() {
      return INSTANCE;
    }

    @Override
    public boolean isAllowedToBeOverwrittenByRemoveStringBuilderAction() {
      return true;
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
        AffectedValues affectedValues,
        StringBuilderOracle oracle) {
      assert oracle.isToString(instruction, instruction.getFirstOperand());
      if (instruction.hasOutValue()) {
        instruction.outValue().addAffectedValuesTo(affectedValues);
      }
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
        AffectedValues affectedValues,
        StringBuilderOracle oracle) {
      Instruction previous = iterator.previous();
      InvokeMethodWithReceiver invoke = previous.asInvokeMethodWithReceiver();
      assert invoke != null;
      Value value = insertStringConstantInstruction(appView, code, iterator, invoke, replacement);
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

    @Override
    public boolean isAllowedToBeOverwrittenByRemoveStringBuilderAction() {
      return true;
    }

    private boolean isAppendWithString(DexMethod method, DexItemFactory factory) {
      return factory.stringBufferMethods.isAppendStringMethod(method)
          || factory.stringBuilderMethods.isAppendStringMethod(method);
    }

    @SuppressWarnings("ReferenceEquality")
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

  class ReplaceByExistingString implements StringBuilderAction {

    private final Value existingString;

    public ReplaceByExistingString(Value existingString) {
      this.existingString = existingString;
    }

    @Override
    public void perform(
        AppView<?> appView,
        IRCode code,
        InstructionListIterator iterator,
        Instruction instruction,
        AffectedValues affectedValues,
        StringBuilderOracle oracle) {
      instruction.outValue().replaceUsers(existingString, affectedValues);
      iterator.removeOrReplaceByDebugLocalRead();
    }

    @Override
    public boolean isAllowedToBeOverwrittenByRemoveStringBuilderAction() {
      return true;
    }
  }

  class ReplaceByStringConcat implements StringBuilderAction {

    private final Value first;
    private final Value second;

    private final String newConstant;

    private ReplaceByStringConcat(Value first, Value second, String newConstant) {
      assert first != null || newConstant != null;
      assert second != null || newConstant != null;
      this.first = first;
      this.second = second;
      this.newConstant = newConstant;
    }

    public static ReplaceByStringConcat replaceByValues(Value first, Value second) {
      return new ReplaceByStringConcat(first, second, null);
    }

    public static ReplaceByStringConcat replaceByNewConstantConcatValue(
        String newConstant, Value second) {
      return new ReplaceByStringConcat(null, second, newConstant);
    }

    public static ReplaceByStringConcat replaceByValueConcatNewConstant(
        Value first, String newConstant) {
      return new ReplaceByStringConcat(first, null, newConstant);
    }

    @Override
    public void perform(
        AppView<?> appView,
        IRCode code,
        InstructionListIterator iterator,
        Instruction instruction,
        AffectedValues affectedValues,
        StringBuilderOracle oracle) {
      Value constString = null;
      if (newConstant != null) {
        Instruction previous = iterator.previous();
        constString =
            insertStringConstantInstruction(appView, code, iterator, previous, newConstant);
        iterator.next();
      }
      assert first != null || constString != null;
      assert second != null || constString != null;
      // To ensure that we do not fail narrowing when evaluating String.concat, we mark the type
      // as maybe null.
      iterator.replaceCurrentInstruction(
          InvokeVirtual.builder()
              .setFreshOutValue(
                  code, TypeElement.stringClassType(appView), instruction.getLocalInfo())
              .setMethod(appView.dexItemFactory().stringMembers.concat)
              .setArguments(
                  ImmutableList.of(
                      first != null ? first : constString, second != null ? second : constString))
              .build());
    }
  }

  class ReplaceArgumentByExistingString implements StringBuilderAction {

    private final Value string;

    public ReplaceArgumentByExistingString(Value string) {
      this.string = string;
    }

    @Override
    public void perform(
        AppView<?> appView,
        IRCode code,
        InstructionListIterator iterator,
        Instruction instruction,
        AffectedValues affectedValues,
        StringBuilderOracle oracle) {
      instruction.replaceValue(1, string);
    }

    @Override
    public boolean isAllowedToBeOverwrittenByRemoveStringBuilderAction() {
      return true;
    }
  }

  class ReplaceArgumentByStringConcat implements StringBuilderAction {

    private final Value first;
    private final Value second;
    private final String newConstant;
    private final Value outValue;
    private boolean removeInstruction;

    private ReplaceArgumentByStringConcat(
        Value first, Value second, String newConstant, Value outValue) {
      assert first != null || newConstant != null;
      assert second != null || newConstant != null;
      this.first = first;
      this.second = second;
      this.newConstant = newConstant;
      this.outValue = outValue;
    }

    public static ReplaceArgumentByStringConcat replaceByValues(
        Value first, Value second, Value outValue) {
      return new ReplaceArgumentByStringConcat(first, second, null, outValue);
    }

    public static ReplaceArgumentByStringConcat replaceByNewConstantConcatValue(
        String newConstant, Value second, Value outValue) {
      return new ReplaceArgumentByStringConcat(null, second, newConstant, outValue);
    }

    public static ReplaceArgumentByStringConcat replaceByValueConcatNewConstant(
        Value first, String newConstant, Value outValue) {
      return new ReplaceArgumentByStringConcat(first, null, newConstant, outValue);
    }

    public void setRemoveInstruction() {
      removeInstruction = true;
    }

    @Override
    public void perform(
        AppView<?> appView,
        IRCode code,
        InstructionListIterator iterator,
        Instruction instruction,
        AffectedValues affectedValues,
        StringBuilderOracle oracle) {
      assert instruction.isInvokeMethod();
      assert instruction.inValues().size() == 2;
      Instruction previous = iterator.previous();
      assert previous == instruction;
      Value constString = null;
      if (newConstant != null) {
        constString =
            insertStringConstantInstruction(appView, code, iterator, previous, newConstant);
      }
      assert first != null || constString != null;
      assert second != null || constString != null;
      InvokeVirtual stringConcat =
          InvokeVirtual.builder()
              .setMethod(appView.dexItemFactory().stringMembers.concat)
              .setOutValue(outValue)
              .setArguments(
                  ImmutableList.of(
                      first != null ? first : constString, second != null ? second : constString))
              .setPosition(instruction.getPosition())
              .build();
      iterator.add(stringConcat);
      Instruction next = iterator.next();
      assert next == instruction;
      if (removeInstruction) {
        removeStringBuilderInstruction(iterator, instruction, affectedValues, oracle);
      } else {
        instruction.replaceValue(1, outValue);
      }
    }

    @Override
    public boolean isReplaceArgumentByStringConcat() {
      return true;
    }

    @Override
    public ReplaceArgumentByStringConcat asReplaceArgumentByStringConcat() {
      return this;
    }
  }

  @SuppressWarnings("ReferenceEquality")
  static DexMethod getConstructorWithStringParameter(
      DexMethod invokedMethod, DexItemFactory factory) {
    if (invokedMethod.getHolderType() == factory.stringBufferType) {
      return factory.stringBufferMethods.stringConstructor;
    } else {
      assert invokedMethod.getHolderType() == factory.stringBuilderType;
      return factory.stringBuilderMethods.stringConstructor;
    }
  }

  static Value insertStringConstantInstruction(
      AppView<?> appView,
      IRCode code,
      InstructionListIterator iterator,
      Instruction instruction,
      String newString) {
    // If the block has catch handlers, inserting a constant string in the same block as the
    // append violates our block representation in DEX since constant string is throwing. If the
    // append is in a block with catch handlers, we simply insert a new constant string in the
    // entry block after all arguments.
    Value value;
    if (!instruction.getBlock().hasCatchHandlers()) {
      value =
          iterator.insertConstStringInstruction(
              appView, code, appView.dexItemFactory().createString(newString));
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
              appView, code, appView.dexItemFactory().createString(newString));
    }
    return value;
  }

  static void removeStringBuilderInstruction(
      InstructionListIterator iterator,
      Instruction instruction,
      AffectedValues affectedValues,
      StringBuilderOracle oracle) {
    assert oracle.isModeledStringBuilderInstruction(
        instruction,
        value ->
            value.getType().isClassType()
                && oracle.isStringBuilderType(value.getType().asClassType().getClassType()));
    if (oracle.isAppend(instruction) && instruction.hasOutValue()) {
      // Append will return the string builder instance. Before removing, ensure that
      // all users of the output values uses the receiver.
      instruction.outValue().replaceUsers(instruction.getFirstOperand(), affectedValues);
    }
    iterator.removeOrReplaceByDebugLocalRead();
  }
}
