// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.synthetic;

import com.android.tools.r8.cf.code.CfCheckCast;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.ValueType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import org.objectweb.asm.Opcodes;

public class ForwardMethodBuilder {

  public static ForwardMethodBuilder builder(DexItemFactory factory) {
    return new ForwardMethodBuilder(factory);
  }

  private enum InvokeType {
    STATIC,
    VIRTUAL,
    SPECIAL
  }

  private final DexItemFactory factory;

  private DexMethod sourceMethod = null;
  private DexMethod targetMethod = null;

  private boolean staticSource = false;

  private InvokeType invokeType = null;
  private Boolean isInterface = null;
  private boolean castResult = false;
  private boolean isConstructorDelegate = false;
  private AppInfoWithClassHierarchy appInfoForCastArguments = null;

  private ForwardMethodBuilder(DexItemFactory factory) {
    this.factory = factory;
  }

  public ForwardMethodBuilder setNonStaticSource(DexMethod method) {
    sourceMethod = method;
    staticSource = false;
    return this;
  }

  public ForwardMethodBuilder setStaticSource(DexMethod method) {
    sourceMethod = method;
    staticSource = true;
    return this;
  }

  public ForwardMethodBuilder setStaticTarget(DexMethod method, boolean isInterface) {
    targetMethod = method;
    invokeType = InvokeType.STATIC;
    this.isInterface = isInterface;
    return this;
  }

  public ForwardMethodBuilder setVirtualTarget(DexMethod method, boolean isInterface) {
    targetMethod = method;
    invokeType = InvokeType.VIRTUAL;
    this.isInterface = isInterface;
    return this;
  }

  public ForwardMethodBuilder setDirectTarget(DexMethod method, boolean isInterface) {
    targetMethod = method;
    invokeType = InvokeType.SPECIAL;
    this.isInterface = isInterface;
    return this;
  }

  public ForwardMethodBuilder setCastResult() {
    castResult = true;
    return this;
  }

  public ForwardMethodBuilder setCastArguments(AppInfoWithClassHierarchy appInfo) {
    appInfoForCastArguments = appInfo;
    return this;
  }

  public ForwardMethodBuilder setConstructorTarget(DexMethod method, DexItemFactory factory) {
    assert method.isInstanceInitializer(factory);
    targetMethod = method;
    isConstructorDelegate = true;
    invokeType = InvokeType.SPECIAL;
    isInterface = false;
    return this;
  }

  public CfCode build() {
    assert validate();
    int maxStack = 0;
    int maxLocals = 0;
    Builder<CfInstruction> instructions = ImmutableList.builder();
    if (isConstructorDelegate) {
      // A constructor delegate allocates a new instance of the type.
      // It is dup'ed on the stack so it is ready to return after the invoke call.
      assert isStaticSource();
      assert invokeType == InvokeType.SPECIAL;
      instructions.add(new CfNew(targetMethod.getHolderType()));
      instructions.add(new CfStackInstruction(Opcode.Dup));
      maxStack += 2;
    } else if (!isStaticSource()) {
      // If source is not static, load the receiver.
      instructions.add(new CfLoad(ValueType.OBJECT, maxLocals));
      maybeInsertArgumentCast(-1, sourceMethod.holder, instructions);
      maxStack += 1;
      maxLocals += 1;
    }
    DexType[] sourceParameters = getSourceParameters();
    for (int i = 0; i < sourceParameters.length; i++) {
      DexType parameter = sourceParameters[i];
      ValueType parameterType = ValueType.fromDexType(parameter);
      instructions.add(new CfLoad(parameterType, maxLocals));
      maxLocals += parameterType.requiredRegisters();
      maybeInsertArgumentCast(i, parameter, instructions);
    }
    instructions.add(new CfInvoke(getInvokeOpcode(), targetMethod, isInterface));
    if (isSourceReturnVoid()) {
      assert !isConstructorDelegate;
      instructions.add(new CfReturnVoid());
    } else {
      if (!isConstructorDelegate && sourceMethod.getReturnType() != targetMethod.getReturnType()) {
        assert castResult;
        if (sourceMethod.getReturnType() != factory.objectType) {
          instructions.add(new CfCheckCast(sourceMethod.getReturnType()));
        }
      }
      instructions.add(new CfReturn(getSourceReturnType()));
    }
    ImmutableList<CfTryCatch> tryCatchRanges = ImmutableList.of();
    ImmutableList<CfCode.LocalVariableInfo> localVariables = ImmutableList.of();
    return new CfCode(
        sourceMethod.holder,
        maxStack,
        maxLocals,
        instructions.build(),
        tryCatchRanges,
        localVariables);
  }

  private void maybeInsertArgumentCast(
      int argumentIndex, DexType sourceArgumentType, Builder<CfInstruction> instructions) {
    if (appInfoForCastArguments == null) {
      return;
    }
    // Shift argument index if mapping between static and non-static.
    if (isStaticSource() != isStaticTarget()) {
      argumentIndex += isStaticSource() ? -1 : 1;
    }
    // Argument -1 is the receiver.
    DexType targetArgumentType =
        argumentIndex == -1
            ? targetMethod.holder
            : targetMethod.getParameters().values[argumentIndex];
    if (sourceArgumentType != targetArgumentType
        && targetArgumentType != appInfoForCastArguments.dexItemFactory().objectType) {
      assert appInfoForCastArguments.isSubtype(targetArgumentType, sourceArgumentType);
      instructions.add(new CfCheckCast(targetArgumentType));
    }
  }

  private int getInvokeOpcode() {
    switch (invokeType) {
      case STATIC:
        return Opcodes.INVOKESTATIC;
      case VIRTUAL:
        return Opcodes.INVOKEVIRTUAL;
      case SPECIAL:
        return Opcodes.INVOKESPECIAL;
    }
    throw new Unreachable("Unexpected invoke type: " + invokeType);
  }

  private DexType[] getSourceParameters() {
    return sourceMethod.getParameters().values;
  }

  private boolean isSourceReturnVoid() {
    return sourceMethod.getReturnType().isVoidType();
  }

  private ValueType getSourceReturnType() {
    assert !isSourceReturnVoid();
    return ValueType.fromDexType(sourceMethod.getReturnType());
  }

  private boolean isStaticSource() {
    return staticSource;
  }

  private boolean isStaticTarget() {
    return invokeType == InvokeType.STATIC;
  }

  private int sourceArguments() {
    return sourceMethod.getParameters().size() + (isStaticSource() ? 0 : 1);
  }

  private int targetArguments() {
    // A constructor delegate will allocate the instance so that is subtracted from args.
    return targetMethod.getParameters().size()
        + (isStaticTarget() || isConstructorDelegate ? 0 : 1);
  }

  private boolean validate() {
    assert sourceMethod != null;
    assert targetMethod != null;
    assert invokeType != null;
    assert isInterface != null;
    assert sourceArguments() == targetArguments();
    if (isConstructorDelegate) {
      assert isStaticSource();
      assert !sourceMethod.getReturnType().isVoidType();
      assert targetMethod.getReturnType().isVoidType();
      assert invokeType == InvokeType.SPECIAL;
    } else if (castResult) {
      assert ValueType.fromDexType(sourceMethod.getReturnType())
          == ValueType.fromDexType(targetMethod.getReturnType());
    } else {
      assert sourceMethod.getReturnType() == targetMethod.getReturnType();
    }
    return true;
  }
}
