// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.CfState;
import com.android.tools.r8.ir.conversion.CfState.Slot;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.naming.NamingLens;
import java.util.Arrays;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CfInvoke extends CfInstruction {

  private final DexMethod method;
  private final int opcode;
  private final boolean itf;

  public CfInvoke(int opcode, DexMethod method, boolean itf) {
    assert Opcodes.INVOKEVIRTUAL <= opcode && opcode <= Opcodes.INVOKEINTERFACE;
    assert !(opcode == Opcodes.INVOKEVIRTUAL && itf) : "InvokeVirtual on interface type";
    assert !(opcode == Opcodes.INVOKEINTERFACE && !itf) : "InvokeInterface on class type";
    this.opcode = opcode;
    this.method = method;
    this.itf = itf;
  }

  public DexMethod getMethod() {
    return method;
  }

  public int getOpcode() {
    return opcode;
  }

  @Override
  public void write(MethodVisitor visitor, NamingLens lens) {
    String owner = lens.lookupInternalName(method.getHolder());
    String name = lens.lookupName(method).toString();
    String desc = method.proto.toDescriptorString(lens);
    visitor.visitMethodInsn(opcode, owner, name, desc, itf);
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Override
  public void registerUse(UseRegistry registry, DexType clazz) {
    switch (opcode) {
      case Opcodes.INVOKEINTERFACE:
        registry.registerInvokeInterface(method);
        break;
      case Opcodes.INVOKEVIRTUAL:
        registry.registerInvokeVirtual(method);
        break;
      case Opcodes.INVOKESPECIAL:
        if (method.name.toString().equals(Constants.INSTANCE_INITIALIZER_NAME)) {
          registry.registerInvokeDirect(method);
        } else if (method.holder == clazz) {
          registry.registerInvokeDirect(method);
        } else {
          registry.registerInvokeSuper(method);
        }
        break;
      case Opcodes.INVOKESTATIC:
        registry.registerInvokeStatic(method);
        break;
      default:
        throw new Unreachable("unknown CfInvoke opcode " + opcode);
    }
  }

  @Override
  public boolean canThrow() {
    return true;
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    Invoke.Type type;
    DexMethod canonicalMethod;
    DexProto callSiteProto = null;
    switch (opcode) {
      case Opcodes.INVOKEINTERFACE:
        {
          canonicalMethod = method;
          type = Type.INTERFACE;
          break;
        }
      case Opcodes.INVOKEVIRTUAL:
        {
          canonicalMethod = builder.getFactory().polymorphicMethods.canonicalize(method);
          if (canonicalMethod == null) {
            type = Type.VIRTUAL;
            canonicalMethod = method;
          } else {
            type = Type.POLYMORPHIC;
            callSiteProto = method.proto;
          }
          break;
        }
      case Opcodes.INVOKESPECIAL:
        {
          canonicalMethod = method;
          if (method.name.toString().equals(Constants.INSTANCE_INITIALIZER_NAME)) {
            type = Type.DIRECT;
          } else if (builder.getMethod().holder == method.holder) {
            type = Type.DIRECT;
          } else {
            type = Type.SUPER;
          }
          break;
        }
      case Opcodes.INVOKESTATIC:
        {
          canonicalMethod = method;
          type = Type.STATIC;
          break;
        }
      default:
        throw new Unreachable("unknown CfInvoke opcode " + opcode);
    }
    int parameterCount = method.proto.parameters.size();
    if (type != Type.STATIC) {
      parameterCount += 1;
    }
    ValueType[] types = new ValueType[parameterCount];
    Integer[] registers = new Integer[parameterCount];
    for (int i = parameterCount - 1; i >= 0; i--) {
      Slot slot = state.pop();
      types[i] = slot.type;
      registers[i] = slot.register;
    }
    builder.addInvoke(
        type, canonicalMethod, callSiteProto, Arrays.asList(types), Arrays.asList(registers), itf);
    if (!method.proto.returnType.isVoidType()) {
      builder.addMoveResult(state.push(method.proto.returnType).register);
    }
  }
}
