// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import com.android.tools.r8.cf.code.CfArrayLoad;
import com.android.tools.r8.cf.code.CfConstNull;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfGoto;
import com.android.tools.r8.cf.code.CfIf;
import com.android.tools.r8.cf.code.CfIfCmp;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfPop;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfStaticGet;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.If.Type;
import com.android.tools.r8.ir.code.ValueType;
import java.util.HashMap;
import java.util.Map;
import org.objectweb.asm.Opcodes;

/**
 * Utility to print CF code and instructions.
 *
 * <p>This implementation prints the code formatted according to the Jasmin syntax.
 */
public class CfPrinter {

  private final String indent;
  private final Map<CfLabel, String> labels;

  private final StringBuilder builder = new StringBuilder();

  /** Entry for printing single instructions without global knowledge (eg, label numbers). */
  public CfPrinter() {
    indent = "";
    labels = null;
  }

  /** Entry for printing a complete code object. */
  public CfPrinter(CfCode code) {
    indent = "  ";
    labels = new HashMap<>();
    int nextLabelNumber = 0;
    for (CfInstruction instruction : code.getInstructions()) {
      if (instruction instanceof CfLabel) {
        labels.put((CfLabel) instruction, "L" + nextLabelNumber++);
      }
    }
    builder.append(".method ");
    appendMethod(code.getMethod());
    newline();
    builder.append(".limit stack ").append(code.getMaxStack());
    newline();
    builder.append(".limit locals ").append(code.getMaxLocals());
    for (CfInstruction instruction : code.getInstructions()) {
      instruction.print(this);
    }
    newline();
    builder.append(".end method");
    newline();
  }

  private void print(String name) {
    indent();
    builder.append(name);
  }

  public void print(CfConstNull constNull) {
    print("aconst_null");
  }

  public void print(CfConstNumber constNumber) {
    indent();
    // TODO(zerny): Determine when the number matches the tconst_X instructions.
    switch (constNumber.getType()) {
      case INT:
        builder.append("ldc ").append(constNumber.getIntValue());
        break;
      case FLOAT:
        builder.append("ldc ").append(constNumber.getFloatValue());
        break;
      case LONG:
        builder.append("ldc_w ").append(constNumber.getLongValue());
        break;
      case DOUBLE:
        builder.append("ldc_w ").append(constNumber.getDoubleValue());
        break;
      default:
        throw new Unreachable("Unexpected const-number type: " + constNumber.getType());
    }
  }

  public void print(CfReturnVoid ret) {
    print("return");
  }

  public void print(CfReturn ret) {
    print(typePrefix(ret.getType()) + "return");
  }

  public void print(CfPop pop) {
    print("pop");
  }

  public void print(CfConstString constString) {
    indent();
    builder.append("ldc ").append(constString.getString());
  }

  public void print(CfArrayLoad arrayLoad) {
    switch (arrayLoad.getType()) {
      case OBJECT:
        print("aaload");
        break;
      case BOOLEAN:
      case BYTE:
        print("baload");
        break;
      case CHAR:
        print("caload");
        break;
      case SHORT:
        print("saload");
        break;
      case INT:
        print("iaload");
        break;
      case FLOAT:
        print("faload");
        break;
      case LONG:
        print("laload");
        break;
      case DOUBLE:
        print("daload");
        break;
      default:
        throw new Unreachable("Unexpected array-load type: " + arrayLoad.getType());
    }
  }

  public void print(CfInvoke invoke) {
    indent();
    switch (invoke.getOpcode()) {
      case Opcodes.INVOKEVIRTUAL:
        builder.append("invokevirtual");
        break;
      case Opcodes.INVOKESPECIAL:
        builder.append("invokespecial");
        break;
      case Opcodes.INVOKESTATIC:
        builder.append("invokestatic");
        break;
      case Opcodes.INVOKEINTERFACE:
        builder.append("invokeinterface");
        break;
      case Opcodes.INVOKEDYNAMIC:
        builder.append("invokedynamic");
        break;
      default:
        throw new Unreachable("Unexpected invoke opcode: " + invoke.getOpcode());
    }
    builder.append(' ');
    appendMethod(invoke.getMethod());
  }

  public void print(CfFrame frame) {
    comment("frame");
  }

  public void print(CfStaticGet staticGet) {
    indent();
    builder.append("getstatic ");
    appendField(staticGet.getField());
    appendDescriptor(staticGet.getField().type);
  }

  public void print(CfNew newInstance) {
    indent();
    builder.append("new ");
    appendClass(newInstance.getType());
  }

  public void print(CfLabel label) {
    newline();
    builder.append(getLabel(label)).append(':');
  }

  public void print(CfGoto jump) {
    indent();
    builder.append("goto ").append(getLabel(jump.getTarget()));
  }

  private String ifPostfix(If.Type kind) {
    return kind.toString().toLowerCase();
  }

  public void print(CfIf conditional) {
    indent();
    if (conditional.getType().isObject()) {
      builder.append("if").append(conditional.getKind() == Type.EQ ? "null" : "nonnull");
    } else {
      builder.append("if").append(ifPostfix(conditional.getKind()));
    }
    builder.append(' ').append(getLabel(conditional.getTarget()));
  }

  public void print(CfIfCmp conditional) {
    indent();
    builder
        .append(conditional.getType().isObject() ? "if_acmp" : "if_icmp")
        .append(ifPostfix(conditional.getKind()))
        .append(' ')
        .append(getLabel(conditional.getTarget()));
  }

  public void print(CfLoad load) {
    print(load.getType(), "load", load.getLocalIndex());
  }

  public void print(CfStore store) {
    print(store.getType(), "store", store.getLocalIndex());
  }

  private void print(ValueType type, String instruction, int local) {
    indent();
    builder.append(typePrefix(type)).append(instruction).append(' ').append(local);
  }

  private char typePrefix(ValueType type) {
    switch (type) {
      case OBJECT:
        return 'a';
      case INT:
        return 'i';
      case FLOAT:
        return 'f';
      case LONG:
        return 'l';
      case DOUBLE:
        return 'd';
      default:
        throw new Unreachable("Unexpected type for prefix: " + type);
    }
  }

  private String getLabel(CfLabel label) {
    return labels != null ? labels.get(label) : "L?";
  }

  private void newline() {
    if (builder.length() > 0) {
      builder.append('\n');
    }
  }

  private void indent() {
    newline();
    builder.append(indent);
  }

  private void comment(String comment) {
    indent();
    builder.append("; ").append(comment);
  }

  private void appendDescriptor(DexType type) {
    builder.append(type.toDescriptorString());
  }

  private void appendClass(DexType type) {
    builder.append(type.getInternalName());
  }

  private void appendField(DexField field) {
    appendClass(field.getHolder());
    builder.append('/').append(field.name);
  }

  private void appendMethod(DexMethod method) {
    builder.append(method.qualifiedName());
    builder.append(method.proto.toDescriptorString());
  }

  @Override
  public String toString() {
    return builder.toString();
  }
}
