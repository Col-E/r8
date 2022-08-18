// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
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
 * using the basic {@code LIRInstructionView}.
 */
public class LIRParsedInstructionCallback implements LIRInstructionCallback {

  private final LIRCode code;

  public LIRParsedInstructionCallback(LIRCode code) {
    this.code = code;
  }

  public void onConstNull() {}

  public void onConstString(DexString string) {}

  public void onInvokeMethodInstruction(DexMethod method, IntList arguments) {}

  public void onInvokeDirect(DexMethod method, IntList arguments) {
    onInvokeMethodInstruction(method, arguments);
  }

  public void onInvokeVirtual(DexMethod method, IntList arguments) {
    onInvokeMethodInstruction(method, arguments);
  }

  public void onFieldInstruction(DexField field) {
    onFieldInstruction(field);
  }

  public void onStaticGet(DexField field) {
    onFieldInstruction(field);
  }

  public void onReturnVoid() {}

  public void onDebugPosition() {}

  private DexItem getConstantItem(int index) {
    return code.getConstantItem(index);
  }

  @Override
  public final void onInstructionView(LIRInstructionView view) {
    switch (view.getOpcode()) {
      case LIROpcodes.ACONST_NULL:
        {
          onConstNull();
          break;
        }
      case LIROpcodes.LDC:
        {
          DexItem item = getConstantItem(view.getNextConstantOperand());
          if (item instanceof DexString) {
            onConstString((DexString) item);
          }
          break;
        }
      case LIROpcodes.INVOKEDIRECT:
        {
          DexMethod target = getInvokeInstructionTarget(view);
          IntList arguments = getInvokeInstructionArguments(view);
          onInvokeDirect(target, arguments);
          break;
        }
      case LIROpcodes.INVOKEVIRTUAL:
        {
          DexMethod target = getInvokeInstructionTarget(view);
          IntList arguments = getInvokeInstructionArguments(view);
          onInvokeVirtual(target, arguments);
          break;
        }
      case LIROpcodes.GETSTATIC:
        {
          DexField field = (DexField) getConstantItem(view.getNextConstantOperand());
          onStaticGet(field);
          break;
        }
      case LIROpcodes.RETURN:
        {
          onReturnVoid();
          break;
        }
      case LIROpcodes.DEBUGPOS:
        {
          onDebugPosition();
          break;
        }
      default:
        throw new Unimplemented("No dispatch for opcode " + LIROpcodes.toString(view.getOpcode()));
    }
  }

  private DexMethod getInvokeInstructionTarget(LIRInstructionView view) {
    return (DexMethod) getConstantItem(view.getNextConstantOperand());
  }

  private IntList getInvokeInstructionArguments(LIRInstructionView view) {
    IntList arguments = new IntArrayList();
    while (view.hasMoreOperands()) {
      arguments.add(view.getNextValueOperand());
    }
    return arguments;
  }
}
