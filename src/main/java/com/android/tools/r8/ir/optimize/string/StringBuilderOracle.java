// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.ir.optimize.string.StringBuilderHelper.extractConstantArgument;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexItemFactory.StringBuildingMethods;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.Value;
import java.util.List;
import java.util.function.Predicate;

/**
 * The {@link StringBuilderOracle} can answer if an instruction is of particular interest to the
 * StringBuilderOptimization.
 */
interface StringBuilderOracle {

  boolean isModeledStringBuilderInstruction(
      Instruction instruction, Predicate<Value> isLiveStringBuilder);

  boolean hasStringBuilderType(Value value);

  boolean isStringBuilderType(DexType type);

  boolean isToString(Instruction instruction, Value value);

  String getConstantArgument(Instruction instruction);

  boolean isInspecting(Instruction instruction);

  boolean isAppend(Instruction instruction);

  boolean canObserveStringBuilderCall(Instruction instruction);

  boolean isInit(Instruction instruction);

  boolean isAppendString(Instruction instruction);

  boolean isStringConstructor(Instruction instruction);

  boolean isConstructorInvokeSideEffectFree(Instruction instruction);

  class DefaultStringBuilderOracle implements StringBuilderOracle {

    private final DexItemFactory factory;

    DefaultStringBuilderOracle(DexItemFactory factory) {
      this.factory = factory;
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public boolean isModeledStringBuilderInstruction(
        Instruction instruction, Predicate<Value> isLiveStringBuilder) {
      if (instruction.isNewInstance()) {
        return isStringBuilderType(instruction.asNewInstance().getType());
      } else if (instruction.isInvokeMethod()) {
        DexMethod invokedMethod = instruction.asInvokeMethod().getInvokedMethod();
        if (isStringBuildingMethod(factory.stringBuilderMethods, invokedMethod)
            || isStringBuildingMethod(factory.stringBufferMethods, invokedMethod)) {
          return true;
        }
        return (invokedMethod == factory.objectMembers.toString
                || invokedMethod == factory.objectsMethods.toStringWithObject)
            && isLiveStringBuilder.test(instruction.getFirstOperand());
      }
      return false;
    }

    @SuppressWarnings("ReferenceEquality")
    private boolean isStringBuildingMethod(StringBuildingMethods methods, DexMethod method) {
      return methods.isAppendMethod(method)
          || methods.isConstructorMethod(method)
          || method == methods.toString
          || method == methods.capacity;
    }

    @Override
    public boolean hasStringBuilderType(Value value) {
      return value.getType().isClassType()
          && isStringBuilderType(value.getType().asClassType().getClassType());
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public boolean isStringBuilderType(DexType type) {
      return type == factory.stringBuilderType || type == factory.stringBufferType;
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public boolean isToString(Instruction instruction, Value value) {
      if (!instruction.isInvokeMethod()) {
        return false;
      }
      if (instruction.inValues().isEmpty()) {
        return false;
      }
      if (instruction.getFirstOperand() != value) {
        return false;
      }
      DexMethod invokedMethod = instruction.asInvokeMethod().getInvokedMethod();
      return factory.stringBuilderMethods.toString == invokedMethod
          || factory.stringBufferMethods.toString == invokedMethod
          || factory.objectMembers.toString == invokedMethod
          || factory.objectsMethods.toStringWithObject == invokedMethod;
    }

    @Override
    public String getConstantArgument(Instruction instruction) {
      if (!instruction.isInvokeMethodWithReceiver()) {
        return null;
      }
      if (isAppend(instruction)
          && !isAppendWithSubArray(instruction.asInvokeMethodWithReceiver())) {
        return getConstantStringForAppend(instruction.asInvokeVirtual());
      } else if (isInit(instruction)) {
        return getConstantStringForInit(instruction.asInvokeDirect());
      }
      return null;
    }

    private DexType getAppendType(InvokeVirtual invokeMethodWithReceiver) {
      DexMethod invokedMethod = invokeMethodWithReceiver.getInvokedMethod();
      if (!factory.stringBuilderMethods.isAppendMethod(invokedMethod)
          && !factory.stringBufferMethods.isAppendMethod(invokedMethod)) {
        return null;
      }
      return invokedMethod.getParameter(0);
    }

    private String getConstantStringForAppend(InvokeVirtual invoke) {
      DexType appendType = getAppendType(invoke);
      Value arg = invoke.getFirstNonReceiverArgument().getAliasedValue();
      return appendType != null
          ? extractConstantArgument(factory, invoke.getInvokedMethod(), arg, appendType)
          : null;
    }

    private String getConstantStringForInit(InvokeDirect invoke) {
      assert invoke.isInvokeConstructor(factory);
      List<Value> inValues = invoke.inValues();
      if (inValues.size() == 1) {
        return "";
      } else if (inValues.size() == 2 && !invoke.getArgument(1).getType().isPrimitiveType()) {
        Value arg = invoke.getArgument(1).getAliasedValue();
        DexType argType = invoke.getInvokedMethod().getParameter(0);
        return argType != null
            ? extractConstantArgument(factory, invoke.getInvokedMethod(), arg, argType)
            : null;
      }
      return null;
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public boolean isInspecting(Instruction instruction) {
      if (!instruction.isInvokeMethodWithReceiver()) {
        return false;
      }
      DexMethod invokedMethod = instruction.asInvokeMethodWithReceiver().getInvokedMethod();
      return factory.stringBuilderMethods.capacity == invokedMethod
          || factory.stringBufferMethods.capacity == invokedMethod;
    }

    @Override
    public boolean isAppend(Instruction instruction) {
      if (!instruction.isInvokeMethod()) {
        return false;
      }
      DexMethod invokedMethod = instruction.asInvokeMethod().getInvokedMethod();
      return factory.stringBuilderMethods.isAppendMethod(invokedMethod)
          || factory.stringBufferMethods.isAppendMethod(invokedMethod);
    }

    public boolean isAppendWithSubArray(InvokeMethodWithReceiver instruction) {
      DexMethod invokedMethod = instruction.asInvokeMethod().getInvokedMethod();
      return factory.stringBuilderMethods.isAppendSubArrayMethod(invokedMethod)
          || factory.stringBufferMethods.isAppendSubArrayMethod(invokedMethod);
    }

    @Override
    public boolean canObserveStringBuilderCall(Instruction instruction) {
      if (!instruction.isInvokeMethod()) {
        assert false : "Expecting a call to string builder";
        return true;
      }
      DexMethod invokedMethod = instruction.asInvokeMethod().getInvokedMethod();
      if (factory.stringBuilderMethods.isAppendObjectOrCharSequenceMethod(invokedMethod)
          || factory.stringBufferMethods.isAppendObjectOrCharSequenceMethod(invokedMethod)) {
        return !instruction.inValues().get(1).getType().isStringType(factory);
      }
      if (invokedMethod.isInstanceInitializer(factory)) {
        return !isConstructorInvokeSideEffectFree(instruction);
      }
      if (factory.stringBuilderMethods.isAppendCharArrayMethod(invokedMethod)
          || factory.stringBufferMethods.isAppendCharArrayMethod(invokedMethod)) {
        return instruction.asInvokeVirtual().getFirstNonReceiverArgument().isMaybeNull();
      }
      return false;
    }

    @Override
    public boolean isInit(Instruction instruction) {
      if (!instruction.isInvokeDirect()) {
        return false;
      }
      DexMethod invokedMethod = instruction.asInvokeMethod().getInvokedMethod();
      return factory.stringBuilderMethods.isConstructorMethod(invokedMethod)
          || factory.stringBufferMethods.isConstructorMethod(invokedMethod);
    }

    @Override
    public boolean isAppendString(Instruction instruction) {
      if (!instruction.isInvokeMethod()) {
        return false;
      }
      DexMethod invokedMethod = instruction.asInvokeMethod().getInvokedMethod();
      return factory.stringBuilderMethods.isAppendStringMethod(invokedMethod)
          || factory.stringBufferMethods.isAppendStringMethod(invokedMethod);
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public boolean isStringConstructor(Instruction instruction) {
      if (!instruction.isInvokeMethod()) {
        return false;
      }
      DexMethod invokedMethod = instruction.asInvokeMethod().getInvokedMethod();
      return invokedMethod == factory.stringBuilderMethods.stringConstructor
          || invokedMethod == factory.stringBufferMethods.stringConstructor;
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public boolean isConstructorInvokeSideEffectFree(Instruction instruction) {
      if (!instruction.isInvokeConstructor(factory)) {
        return false;
      }
      DexMethod invokedMethod = instruction.asInvokeMethod().getInvokedMethod();
      if (invokedMethod.getHolderType() == factory.stringBuilderType) {
        return factory.stringBuilderMethods.constructorInvokeIsSideEffectFree(
            invokedMethod, instruction.inValues());
      } else {
        assert invokedMethod.getHolderType() == factory.stringBufferType;
        return factory.stringBufferMethods.constructorInvokeIsSideEffectFree(
            invokedMethod, instruction.inValues());
      }
    }
  }
}
