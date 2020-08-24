// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.InitClassLens;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.CfState;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.naming.NamingLens;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class CfInvokeDynamic extends CfInstruction {

  private final DexCallSite callSite;

  public CfInvokeDynamic(DexCallSite callSite) {
    this.callSite = callSite;
  }

  @Override
  public void write(MethodVisitor visitor, InitClassLens initClassLens, NamingLens lens) {
    DexMethodHandle bootstrapMethod = callSite.bootstrapMethod;
    List<DexValue> bootstrapArgs = callSite.bootstrapArgs;
    Object[] bsmArgs = new Object[bootstrapArgs.size()];
    for (int i = 0; i < bootstrapArgs.size(); i++) {
      bsmArgs[i] = decodeBootstrapArgument(bootstrapArgs.get(i), lens);
    }
    Handle bsmHandle = bootstrapMethod.toAsmHandle(lens);
    DexString methodName = lens.lookupMethodName(callSite);
    visitor.visitInvokeDynamicInsn(
        methodName.toString(), callSite.methodProto.toDescriptorString(lens), bsmHandle, bsmArgs);
  }

  private Object decodeBootstrapArgument(DexValue value, NamingLens lens) {
    switch (value.getValueKind()) {
      case DOUBLE:
        return value.asDexValueDouble().getValue();
      case FLOAT:
        return value.asDexValueFloat().getValue();
      case INT:
        return value.asDexValueInt().getValue();
      case LONG:
        return value.asDexValueLong().getValue();
      case METHOD_HANDLE:
        return value.asDexValueMethodHandle().getValue().toAsmHandle(lens);
      case METHOD_TYPE:
        return Type.getMethodType(value.asDexValueMethodType().getValue().toDescriptorString(lens));
      case STRING:
        DexString innerValue = value.asDexValueString().getValue();
        return innerValue == null ? null : innerValue.toString();
      case TYPE:
        return Type.getType(lens.lookupDescriptor(value.asDexValueType().value).toString());
      default:
        throw new Unreachable(
            "Unsupported bootstrap argument of type " + value.getClass().getSimpleName());
    }
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  public DexCallSite getCallSite() {
    return callSite;
  }

  @Override
  void internalRegisterUse(UseRegistry registry, DexClassAndMethod context) {
    registry.registerCallSite(callSite);
  }

  @Override
  public boolean canThrow() {
    return true;
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    DexType[] parameterTypes = callSite.methodProto.parameters.values;
    List<Integer> registers = new ArrayList<>(parameterTypes.length);
    for (int register : state.popReverse(parameterTypes.length)) {
      registers.add(register);
    }
    List<ValueType> types = new ArrayList<>(parameterTypes.length);
    for (DexType value : parameterTypes) {
      types.add(ValueType.fromDexType(value));
    }
    builder.addInvokeCustom(callSite, types, registers);
    if (!callSite.methodProto.returnType.isVoidType()) {
      builder.addMoveResult(state.push(callSite.methodProto.returnType).register);
    }
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, DexProgramClass context) {
    return inliningConstraints.forInvokeCustom();
  }
}
