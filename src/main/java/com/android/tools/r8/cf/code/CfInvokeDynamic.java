// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.CfCompareHelper;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.InitClassLens;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.CfState;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.optimize.interfaces.analysis.CfAnalysisConfig;
import com.android.tools.r8.optimize.interfaces.analysis.CfFrameState;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class CfInvokeDynamic extends CfInstruction {

  private final DexCallSite callSite;

  public CfInvokeDynamic(DexCallSite callSite) {
    this.callSite = callSite;
  }

  @Override
  public boolean isInvokeDynamic() {
    return true;
  }

  @Override
  public CfInvokeDynamic asInvokeDynamic() {
    return this;
  }

  @Override
  public int getCompareToId() {
    return Opcodes.INVOKEDYNAMIC;
  }

  @Override
  public int internalAcceptCompareTo(
      CfInstruction other, CompareToVisitor visitor, CfCompareHelper helper) {
    return callSite.acceptCompareTo(((CfInvokeDynamic) other).callSite, visitor);
  }

  @Override
  public void internalAcceptHashing(HashingVisitor visitor) {
    callSite.acceptHashing(visitor);
  }

  @Override
  public void write(
      AppView<?> appView,
      ProgramMethod context,
      DexItemFactory dexItemFactory,
      GraphLens graphLens,
      InitClassLens initClassLens,
      NamingLens namingLens,
      LensCodeRewriterUtils rewriter,
      MethodVisitor visitor) {
    DexCallSite rewrittenCallSite = rewriter.rewriteCallSite(callSite, context);
    DexMethodHandle bootstrapMethod = rewrittenCallSite.bootstrapMethod;
    List<DexValue> bootstrapArgs = rewrittenCallSite.bootstrapArgs;
    Object[] bsmArgs = new Object[bootstrapArgs.size()];
    for (int i = 0; i < bootstrapArgs.size(); i++) {
      bsmArgs[i] = decodeBootstrapArgument(bootstrapArgs.get(i), namingLens);
    }
    Handle bsmHandle = bootstrapMethod.toAsmHandle(namingLens);
    DexString methodName = namingLens.lookupMethodName(rewrittenCallSite, appView);
    visitor.visitInvokeDynamicInsn(
        methodName.toString(),
        rewrittenCallSite.methodProto.toDescriptorString(namingLens),
        bsmHandle,
        bsmArgs);
  }

  @Override
  public int bytecodeSizeUpperBound() {
    return 5;
  }

  public static Object decodeBootstrapArgument(DexValue value, NamingLens lens) {
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
  void internalRegisterUse(
      UseRegistry<?> registry, DexClassAndMethod context, ListIterator<CfInstruction> iterator) {
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
      InliningConstraints inliningConstraints, CfCode code, ProgramMethod context) {
    return inliningConstraints.forInvokeCustom();
  }

  @Override
  public CfFrameState evaluate(CfFrameState frame, AppView<?> appView, CfAnalysisConfig config) {
    // ..., [arg1, [arg2 ...]] →
    // ...
    frame =
        frame.popInitialized(
            appView, config, callSite.getMethodProto().getParameters().getBacking());
    DexType returnType = callSite.getMethodProto().getReturnType();
    if (returnType.isVoidType()) {
      return frame;
    }
    return frame.push(config, returnType);
  }
}
