// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.synthetic;

import static com.android.tools.r8.utils.ConsumerUtils.emptyConsumer;

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
import com.android.tools.r8.utils.BooleanUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import java.util.function.Consumer;
import org.objectweb.asm.Opcodes;

public class ForwardMethodBuilder {

  public static ForwardMethodBuilder builder(DexItemFactory factory) {
    return new ForwardMethodBuilder(factory);
  }

  private enum InvokeType {
    STATIC,
    VIRTUAL,
    INTERFACE,
    SPECIAL,
  }

  private final DexItemFactory factory;

  private DexMethod sourceMethod = null;
  private DexMethod targetMethod = null;

  private boolean sourceMethodHasExtraUnusedParameter = false;
  private boolean staticSource = false;

  private InvokeType invokeType = null;
  private Boolean isInterface = null;
  private boolean castResult = false;
  private boolean isConstructorDelegate = false;
  private AppInfoWithClassHierarchy appInfoForCastArguments = null;

  private ForwardMethodBuilder(DexItemFactory factory) {
    this.factory = factory;
  }

  public ForwardMethodBuilder apply(Consumer<ForwardMethodBuilder> fn) {
    fn.accept(this);
    return this;
  }

  public ForwardMethodBuilder applyIf(
      boolean condition, Consumer<ForwardMethodBuilder> thenConsumer) {
    return applyIf(condition, thenConsumer, emptyConsumer());
  }

  public ForwardMethodBuilder applyIf(
      boolean condition,
      Consumer<ForwardMethodBuilder> thenConsumer,
      Consumer<ForwardMethodBuilder> elseConsumer) {
    if (condition) {
      thenConsumer.accept(this);
    } else {
      elseConsumer.accept(this);
    }
    return this;
  }

  public ForwardMethodBuilder setNonStaticSource(DexMethod method) {
    sourceMethod = method;
    staticSource = false;
    return this;
  }

  public ForwardMethodBuilder setNonStaticSourceWithExtraUnusedParameter(DexMethod method) {
    sourceMethod = method;
    staticSource = false;
    sourceMethodHasExtraUnusedParameter = true;
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

  public ForwardMethodBuilder setSuperTarget(DexMethod method, boolean isInterface) {
    targetMethod = method;
    invokeType = InvokeType.SPECIAL;
    this.isInterface = isInterface;
    return this;
  }

  public ForwardMethodBuilder setVirtualTarget(DexMethod method, boolean isInterface) {
    targetMethod = method;
    invokeType = isInterface ? InvokeType.INTERFACE : InvokeType.VIRTUAL;
    this.isInterface = isInterface;
    return this;
  }

  public ForwardMethodBuilder setConstructorTarget(DexMethod method) {
    return setDirectTarget(method, false);
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

  public ForwardMethodBuilder setConstructorTargetWithNewInstance(DexMethod method) {
    assert method.isInstanceInitializer(factory);
    targetMethod = method;
    isConstructorDelegate = true;
    invokeType = InvokeType.SPECIAL;
    isInterface = false;
    return this;
  }

  @SuppressWarnings({"BadImport", "ReferenceEquality"})
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
      if (!(sourceMethodHasExtraUnusedParameter && i == sourceParameters.length - 1)) {
        // We do not need to load the last parameter if it is unused.
        // We still need to increase the maxStack/maxLocals values for the method to verify.
        instructions.add(new CfLoad(parameterType, maxLocals));
        maybeInsertArgumentCast(i, parameter, instructions);
      }
      maxStack += parameterType.requiredRegisters();
      maxLocals += parameterType.requiredRegisters();
    }
    instructions.add(new CfInvoke(getInvokeOpcode(), targetMethod, isInterface));
    if (!targetMethod.getReturnType().isVoidType()) {
      // If the return type is not void, it will push a value on the stack. We subtract the
      // arguments pushed by the invoke to see if bumping the stack height is necessary.
      maxStack =
          Math.max(
              maxStack, ValueType.fromDexType(targetMethod.getReturnType()).requiredRegisters());
    }
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

  @SuppressWarnings({"BadImport", "ReferenceEquality"})
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
      case INTERFACE:
        return Opcodes.INVOKEINTERFACE;
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
    return sourceMethod.getParameters().size()
        + (isStaticSource() ? 0 : 1)
        - BooleanUtils.intValue(sourceMethodHasExtraUnusedParameter);
  }

  private int targetArguments() {
    // A constructor delegate will allocate the instance so that is subtracted from args.
    return targetMethod.getParameters().size()
        + (isStaticTarget() || isConstructorDelegate ? 0 : 1);
  }

  @SuppressWarnings("ReferenceEquality")
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
    } else if (castResult
        && !sourceMethod.getReturnType().isVoidType()
        && !targetMethod.getReturnType().isVoidType()) {
      assert ValueType.fromDexType(sourceMethod.getReturnType())
          == ValueType.fromDexType(targetMethod.getReturnType());
    } else {
      assert sourceMethod.getReturnType() == targetMethod.getReturnType();
    }
    return true;
  }
}
