// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.NumericType;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

/**
 * Structured callbacks for interpreting LIR.
 *
 * <p>This callback parses the actual instructions and dispatches to instruction specific methods
 * where the parsed data is provided as arguments. Instructions that are part of a family of
 * instructions have a default implementation that will call the "instruction family" methods (e.g.,
 * onInvokeVirtual will default dispatch to onInvokedMethodInstruction).
 *
 * <p>Due to the parsing of the individual instructions, this parser has a higher overhead than
 * using the basic {@link LirInstructionView}.
 */
public abstract class LirParsedInstructionCallback implements LirInstructionCallback {

  private final LirCode code;

  public LirParsedInstructionCallback(LirCode code) {
    this.code = code;
  }

  /** Returns the index for the value associated with the current argument/instruction. */
  public abstract int getCurrentValueIndex();

  public final int getCurrentInstructionIndex() {
    return getCurrentValueIndex() - code.getArgumentCount();
  }

  private int getActualValueIndex(int relativeValueIndex) {
    return code.decodeValueIndex(relativeValueIndex, getCurrentValueIndex());
  }

  private int getNextValueOperand(LirInstructionView view) {
    return getActualValueIndex(view.getNextValueOperand());
  }

  public void onInstruction() {}

  public void onConstNull() {
    onInstruction();
  }

  public void onConstNumber(NumericType type, long value) {
    onInstruction();
  }

  public void onConstInt(int value) {
    onConstNumber(NumericType.INT, value);
  }

  public void onConstString(DexString string) {
    onInstruction();
  }

  public void onDiv(NumericType type, int leftValueIndex, int rightValueIndex) {
    onInstruction();
  }

  public void onDivInt(int leftValueIndex, int rightValueIndex) {
    onDiv(NumericType.INT, leftValueIndex, rightValueIndex);
  }

  public void onIf(If.Type ifKind, int blockIndex, int valueIndex) {
    onInstruction();
  }

  public void onGoto(int blockIndex) {
    onInstruction();
  }

  public void onFallthrough() {
    onInstruction();
  }

  public void onMoveException(DexType exceptionType) {
    onInstruction();
  }

  public void onDebugLocalWrite(int srcIndex) {
    onInstruction();
  }

  public void onInvokeMethodInstruction(DexMethod method, IntList arguments) {
    onInstruction();
  }

  public void onInvokeDirect(DexMethod method, IntList arguments) {
    onInvokeMethodInstruction(method, arguments);
  }

  public void onInvokeVirtual(DexMethod method, IntList arguments) {
    onInvokeMethodInstruction(method, arguments);
  }

  public void onFieldInstruction(DexField field) {
    onInstruction();
  }

  public void onStaticGet(DexField field) {
    onFieldInstruction(field);
  }

  public void onReturnVoid() {
    onInstruction();
  }

  public void onArrayLength(int arrayValueIndex) {
    onInstruction();
  }

  public void onDebugPosition() {
    onInstruction();
  }

  public void onPhi(DexType type, IntList operands) {
    onInstruction();
  }

  private DexItem getConstantItem(int index) {
    return code.getConstantItem(index);
  }

  @Override
  public void onInstructionView(LirInstructionView view) {
    int opcode = view.getOpcode();
    switch (opcode) {
      case LirOpcodes.ACONST_NULL:
        {
          onConstNull();
          return;
        }
      case LirOpcodes.LDC:
        {
          DexItem item = getConstantItem(view.getNextConstantOperand());
          if (item instanceof DexString) {
            onConstString((DexString) item);
            return;
          }
          throw new Unimplemented();
        }
      case LirOpcodes.ICONST_M1:
      case LirOpcodes.ICONST_0:
      case LirOpcodes.ICONST_1:
      case LirOpcodes.ICONST_2:
      case LirOpcodes.ICONST_3:
      case LirOpcodes.ICONST_4:
      case LirOpcodes.ICONST_5:
        {
          int value = opcode - LirOpcodes.ICONST_0;
          onConstInt(value);
          return;
        }
      case LirOpcodes.ICONST:
        {
          int value = view.getNextIntegerOperand();
          onConstInt(value);
          return;
        }
      case LirOpcodes.IDIV:
        {
          int leftValueIndex = getNextValueOperand(view);
          int rightValueIndex = getNextValueOperand(view);
          onDivInt(leftValueIndex, rightValueIndex);
          return;
        }
      case LirOpcodes.IFNE:
        {
          int blockIndex = view.getNextBlockOperand();
          int valueIndex = getNextValueOperand(view);
          onIf(If.Type.NE, blockIndex, valueIndex);
          return;
        }
      case LirOpcodes.GOTO:
        {
          int blockIndex = view.getNextBlockOperand();
          onGoto(blockIndex);
          return;
        }
      case LirOpcodes.INVOKEDIRECT:
        {
          DexMethod target = getInvokeInstructionTarget(view);
          IntList arguments = getInvokeInstructionArguments(view);
          onInvokeDirect(target, arguments);
          return;
        }
      case LirOpcodes.INVOKEVIRTUAL:
        {
          DexMethod target = getInvokeInstructionTarget(view);
          IntList arguments = getInvokeInstructionArguments(view);
          onInvokeVirtual(target, arguments);
          return;
        }
      case LirOpcodes.GETSTATIC:
        {
          DexField field = (DexField) getConstantItem(view.getNextConstantOperand());
          onStaticGet(field);
          return;
        }
      case LirOpcodes.RETURN:
        {
          onReturnVoid();
          return;
        }
      case LirOpcodes.ARRAYLENGTH:
        {
          onArrayLength(getNextValueOperand(view));
          return;
        }
      case LirOpcodes.DEBUGPOS:
        {
          onDebugPosition();
          return;
        }
      case LirOpcodes.PHI:
        {
          DexType type = (DexType) getConstantItem(view.getNextConstantOperand());
          IntList operands = new IntArrayList();
          while (view.hasMoreOperands()) {
            operands.add(getNextValueOperand(view));
          }
          onPhi(type, operands);
          return;
        }
      case LirOpcodes.FALLTHROUGH:
        {
          onFallthrough();
          return;
        }
      case LirOpcodes.MOVEEXCEPTION:
        {
          DexType type = (DexType) getConstantItem(view.getNextConstantOperand());
          onMoveException(type);
          return;
        }
      case LirOpcodes.DEBUGLOCALWRITE:
        {
          int srcIndex = getNextValueOperand(view);
          onDebugLocalWrite(srcIndex);
          return;
        }
      default:
        throw new Unimplemented("No dispatch for opcode " + LirOpcodes.toString(opcode));
    }
  }

  private DexMethod getInvokeInstructionTarget(LirInstructionView view) {
    return (DexMethod) getConstantItem(view.getNextConstantOperand());
  }

  private IntList getInvokeInstructionArguments(LirInstructionView view) {
    IntList arguments = new IntArrayList();
    while (view.hasMoreOperands()) {
      arguments.add(getNextValueOperand(view));
    }
    return arguments;
  }
}
