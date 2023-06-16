// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.CatchHandlers.CatchHandler;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.lightir.LirBuilder.IntSwitchPayload;
import com.android.tools.r8.naming.dexitembasedstring.NameComputationInfo;
import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

public class LirPrinter<EV> extends LirParsedInstructionCallback<EV> {

  private static final String SEPERATOR = "\n";
  private final LirCode<EV> code;
  private final StringBuilder builder = new StringBuilder();

  private final int instructionIndexPadding;
  private final int instructionNamePadding;

  private int valueIndex = 0;
  private LirInstructionView view;

  public LirPrinter(LirCode<EV> code) {
    super(code);
    this.code = code;
    instructionIndexPadding =
        Math.max(
            fmtInsnIndex(-code.getArgumentCount()).length(),
            fmtInsnIndex(code.getInstructionCount() - 1).length());
    int maxNameLength = 0;
    for (LirInstructionView view : code) {
      maxNameLength = Math.max(maxNameLength, LirOpcodes.toString(view.getOpcode()).length());
    }
    instructionNamePadding = maxNameLength;
  }

  @Override
  public int getCurrentValueIndex() {
    return valueIndex;
  }

  private void advanceToNextValueIndex() {
    valueIndex++;
  }

  private String fmtValueIndex(int valueIndex) {
    return "v" + valueIndex;
  }

  private String fmtValueIndex(EV valueIndex) {
    return "v" + valueIndex.toString();
  }

  private String fmtInsnIndex(int instructionIndex) {
    return instructionIndex < 0 ? "--" : ("" + instructionIndex);
  }

  @SafeVarargs
  private void appendValueArguments(EV... arguments) {
    appendValueArguments(Arrays.asList(arguments));
  }

  private void appendValueArguments(List<EV> arguments) {
    for (int i = 0; i < arguments.size(); i++) {
      builder.append(fmtValueIndex(arguments.get(i))).append(' ');
    }
  }

  public String prettyPrint() {
    for (int i = 0; i < code.getArgumentCount(); i++) {
      addInstructionHeader("ARG", 0);
      appendOutValue();
      advanceToNextValueIndex();
    }
    code.forEach(this::onInstructionView);
    if (code.getTryCatchTable() != null) {
      builder.append("try-catch-handlers:\n");
      code.getTryCatchTable()
          .tryCatchHandlers
          .forEach(
              (index, handlers) -> {
                builder.append(index).append(":\n");
                for (CatchHandler<Integer> handler : handlers) {
                  builder
                      .append(handler.getGuard())
                      .append(" -> ")
                      .append(handler.getTarget())
                      .append('\n');
                }
              });
    }
    return builder.toString();
  }

  @Override
  public void onInstructionView(LirInstructionView view) {
    this.view = view;
    assert view.getInstructionIndex() == getCurrentInstructionIndex();
    int operandSizeInBytes = view.getRemainingOperandSizeInBytes();
    int instructionSizeInBytes = operandSizeInBytes == 0 ? 1 : 2 + operandSizeInBytes;
    String opcode = LirOpcodes.toString(view.getOpcode());
    addInstructionHeader(opcode, instructionSizeInBytes);
    super.onInstructionView(view);
    advanceToNextValueIndex();
  }

  private void addInstructionHeader(String opcode, int instructionSize) {
    if (getCurrentValueIndex() > 0) {
      builder.append(SEPERATOR);
    }
    StringUtils.appendLeftPadded(
        builder, fmtInsnIndex(getCurrentInstructionIndex()), instructionIndexPadding);
    builder.append(':');
    StringUtils.appendLeftPadded(
        builder, Integer.toString(instructionSize), instructionIndexPadding);
    builder.append(": ");
    StringUtils.appendRightPadded(builder, opcode, instructionNamePadding);
    builder.append(' ');
  }

  @Override
  public void onInstruction() {
    throw new Unimplemented(
        "Printing of instruction missing: " + LirOpcodes.toString(view.getOpcode()));
  }

  private StringBuilder appendOutValue() {
    return builder.append(fmtValueIndex(getCurrentValueIndex())).append(" <- ");
  }

  @Override
  public void onConstNull() {
    appendOutValue().append("null");
  }

  @Override
  public void onConstInt(int value) {
    appendOutValue().append(value);
  }

  @Override
  public void onConstFloat(int value) {
    appendOutValue().append(Float.intBitsToFloat(value));
  }

  @Override
  public void onConstLong(long value) {
    appendOutValue().append(value);
  }

  @Override
  public void onConstDouble(long value) {
    appendOutValue().append(Double.longBitsToDouble(value));
  }

  @Override
  public void onConstString(DexString string) {
    appendOutValue().append("str(").append(string).append(")");
  }

  @Override
  public void onDexItemBasedConstString(
      DexReference item, NameComputationInfo<?> nameComputationInfo) {
    appendOutValue().append("item(").append(item).append(")");
  }

  @Override
  public void onConstClass(DexType type, boolean ignoreCompatRules) {
    appendOutValue().append("class(").append(type).append(")");
  }

  @Override
  public void onConstMethodHandle(DexMethodHandle methodHandle) {
    appendOutValue().append("methodHandle(").append(methodHandle).append(")");
  }

  @Override
  public void onConstMethodType(DexProto methodType) {
    appendOutValue().append("methodType(").append(methodType).append(")");
  }

  @Override
  public void onBinop(NumericType type, EV left, EV right) {
    appendOutValue();
    appendValueArguments(left, right);
  }

  @Override
  public void onNeg(NumericType type, EV value) {
    appendOutValue();
    appendValueArguments(value);
  }

  @Override
  public void onNot(NumericType type, EV value) {
    appendOutValue();
    appendValueArguments(value);
  }

  @Override
  public void onNumberConversion(int opcode, EV value) {
    appendOutValue();
    appendValueArguments(value);
  }

  @Override
  public void onIf(IfType ifKind, int blockIndex, EV valueIndex) {
    appendValueArguments(valueIndex);
    builder.append(fmtInsnIndex(blockIndex));
  }

  @Override
  public void onIfCmp(IfType ifKind, int blockIndex, EV leftValueIndex, EV rightValueIndex) {
    appendValueArguments(leftValueIndex, rightValueIndex);
    builder.append(fmtInsnIndex(blockIndex));
  }

  @Override
  public void onGoto(int blockIndex) {
    builder.append(fmtInsnIndex(blockIndex));
  }

  @Override
  public void onIntSwitch(EV value, IntSwitchPayload payload) {
    appendValueArguments(value);
    // TODO(b/225838009): Consider printing the switch payload info.
  }

  @Override
  public void onFallthrough() {
    // Nothing to append.
  }

  @Override
  public void onMoveException(DexType exceptionType) {
    appendOutValue().append(exceptionType);
  }

  @Override
  public void onDebugLocalWrite(EV srcIndex) {
    appendOutValue().append(fmtValueIndex(srcIndex));
  }

  @Override
  public void onDebugLocalRead() {
    // Nothing to add.
  }

  @Override
  public void onInvokeMultiNewArray(DexType type, List<EV> arguments) {
    appendOutValue();
    appendValueArguments(arguments);
    builder.append(type);
  }

  @Override
  public void onInvokeNewArray(DexType type, List<EV> arguments) {
    appendOutValue();
    appendValueArguments(arguments);
    builder.append(type);
  }

  @Override
  public void onNewArrayFilledData(int elementWidth, long size, short[] data, EV src) {
    appendValueArguments(src);
    builder.append("w:").append(elementWidth).append(",s:").append(size);
  }

  @Override
  public void onNewInstance(DexType clazz) {
    appendOutValue();
    builder.append(clazz);
  }

  @Override
  public void onInvokeMethodInstruction(DexMethod method, List<EV> arguments) {
    if (!method.getReturnType().isVoidType()) {
      appendOutValue();
    }
    appendValueArguments(arguments);
    builder.append(method);
  }

  @Override
  public void onInvokeCustom(DexCallSite callSite, List<EV> arguments) {
    appendValueArguments(arguments);
    builder.append(callSite);
  }

  @Override
  public void onInvokePolymorphic(DexMethod target, DexProto proto, List<EV> arguments) {
    appendValueArguments(arguments);
    builder.append(target).append(' ').append(proto);
  }

  @Override
  public void onStaticGet(DexField field) {
    appendOutValue();
    builder.append(field).append(' ');
  }

  @Override
  public void onStaticPut(DexField field, EV value) {
    builder.append(field).append(' ');
    appendValueArguments(value);
  }

  @Override
  public void onInstanceGet(DexField field, EV object) {
    appendOutValue();
    builder.append(field).append(' ');
    appendValueArguments(object);
  }

  @Override
  public void onInstancePut(DexField field, EV object, EV value) {
    builder.append(field).append(' ');
    appendValueArguments(object, value);
  }

  @Override
  public void onNewArrayEmpty(DexType type, EV size) {
    appendOutValue();
    builder.append(type).append(' ');
    appendValueArguments(size);
  }

  @Override
  public void onThrow(EV exception) {
    appendValueArguments(exception);
  }

  @Override
  public void onReturnVoid() {
    // Nothing to append.
  }

  @Override
  public void onReturn(EV value) {
    appendValueArguments(value);
  }

  @Override
  public void onArrayLength(EV arrayValueIndex) {
    appendOutValue().append(fmtValueIndex(arrayValueIndex));
  }

  @Override
  public void onCheckCast(DexType type, EV value, boolean ignoreCompatRules) {
    appendOutValue();
    appendValueArguments(value);
    builder.append(type);
  }

  @Override
  public void onSafeCheckCast(DexType type, EV value) {
    onCheckCast(type, value, true);
  }

  @Override
  public void onInstanceOf(DexType type, EV value) {
    appendOutValue();
    appendValueArguments(value);
    builder.append(type);
  }

  @Override
  public void onArrayGetPrimitive(MemberType type, EV array, EV index) {
    appendOutValue();
    appendValueArguments(array, index);
    builder.append(type);
  }

  @Override
  public void onArrayGetObject(DexType type, EV array, EV index) {
    appendOutValue();
    appendValueArguments(array, index);
    builder.append(type);
  }

  @Override
  public void onArrayPut(MemberType type, EV array, EV index, EV value) {
    appendValueArguments(array, index, value);
    builder.append(type);
  }

  @Override
  public void onDebugPosition() {
    // Nothing to append.
  }

  @Override
  public void onPhi(DexType type, List<EV> operands) {
    appendOutValue();
    appendValueArguments(operands);
    builder.append(type);
  }

  @Override
  public void onCmpInstruction(int opcode, EV leftValue, EV rightValue) {
    appendOutValue();
    appendValueArguments(leftValue, rightValue);
  }

  @Override
  public void onMonitorEnter(EV value) {
    appendValueArguments(value);
  }

  @Override
  public void onMonitorExit(EV value) {
    appendValueArguments(value);
  }

  @Override
  public void onNewUnboxedEnumInstance(DexType type, int ordinal) {
    appendOutValue().append("type(").append(type).append(") ordinal(").append(ordinal).append(")");
  }

  @Override
  public void onInitClass(DexType clazz) {
    builder.append(clazz);
  }

  @Override
  public void onRecordFieldValues(DexField[] fields, List<EV> values) {
    appendValueArguments(values);
  }
}
