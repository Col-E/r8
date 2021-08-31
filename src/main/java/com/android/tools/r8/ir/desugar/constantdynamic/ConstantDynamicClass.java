// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.constantdynamic;

import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.PUTSTATIC;

import com.android.tools.r8.cf.code.CfCheckCast;
import com.android.tools.r8.cf.code.CfConstClass;
import com.android.tools.r8.cf.code.CfConstDynamic;
import com.android.tools.r8.cf.code.CfConstNull;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfFrame.FrameType;
import com.android.tools.r8.cf.code.CfGoto;
import com.android.tools.r8.cf.code.CfIf;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfMonitor;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.cf.code.CfThrow;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.Monitor;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.synthesis.SyntheticProgramClassBuilder;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.collections.ImmutableDeque;
import com.android.tools.r8.utils.collections.ImmutableInt2ReferenceSortedMap;
import com.google.common.collect.ImmutableList;
import java.util.List;

public class ConstantDynamicClass {
  public static final String INITIALIZED_FIELD_NAME = "INITIALIZED";
  public static final String CONST_FIELD_NAME = "CONST";

  final AppView<?> appView;
  final ConstantDynamicInstructionDesugaring desugaring;
  private final DexType accessedFrom;
  public ConstantDynamicReference reference;
  public final DexField initializedValueField;
  public final DexField constantValueField;
  final DexMethod getConstMethod;

  // Considered final but is set after due to circularity in allocation.
  private DexProgramClass clazz = null;

  public ConstantDynamicClass(
      SyntheticProgramClassBuilder builder,
      AppView<?> appView,
      ConstantDynamicInstructionDesugaring desugaring,
      ProgramMethod accessedFrom,
      CfConstDynamic constantDynamic) {
    DexItemFactory factory = appView.dexItemFactory();
    this.appView = appView;
    this.desugaring = desugaring;
    this.accessedFrom = accessedFrom.getHolderType();
    this.reference = constantDynamic.getReference();
    this.constantValueField =
        factory.createField(
            builder.getType(), constantDynamic.getType(), factory.createString(CONST_FIELD_NAME));
    this.initializedValueField =
        factory.createField(
            builder.getType(), factory.booleanType, factory.createString(INITIALIZED_FIELD_NAME));
    this.getConstMethod =
        factory.createMethod(
            builder.getType(),
            factory.createProto(constantDynamic.getType()),
            factory.createString("get"));

    synthesizeConstantDynamicClass(builder);
  }

  /*
    Generate code following this pattern:

    class CondySyntheticXXX {
      private static boolean INITIALIZED;
      private static <constant type> CONST;

     public static get() {
        if (!INITIALIZED) {
          synchronized (CondySyntheticXXX.class) {
            if (!INITIALIZED) {
              CONST = bsm(null, "constant name", <constant type>);
              INITIALIZED = true;
            }
          }
        }
        return value;
      }
    }

  */
  private void synthesizeConstantDynamicClass(SyntheticProgramClassBuilder builder) {
    synthesizeStaticFields(builder);
    synthesizeDirectMethods(builder);
  }

  private void synthesizeStaticFields(SyntheticProgramClassBuilder builder) {
    builder.setStaticFields(
        ImmutableList.of(
            DexEncodedField.builder()
                .setField(this.initializedValueField)
                .setAccessFlags(FieldAccessFlags.createPrivateStaticSynthetic())
                .setApiLevel(AndroidApiLevel.minApiLevelIfEnabledOrUnknown(appView))
                .setD8R8Synthesized()
                .build(),
            DexEncodedField.builder()
                .setField(this.constantValueField)
                .setAccessFlags(FieldAccessFlags.createPrivateStaticSynthetic())
                .setApiLevel(AndroidApiLevel.minApiLevelIfEnabledOrUnknown(appView))
                .setD8R8Synthesized()
                .build()));
  }

  private void synthesizeDirectMethods(SyntheticProgramClassBuilder builder) {
    builder.setDirectMethods(
        ImmutableList.of(
            DexEncodedMethod.builder()
                .setMethod(getConstMethod)
                .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                .setCode(generateGetterCode(builder))
                .setD8R8Synthesized()
                .setApiLevelForDefinition(AndroidApiLevel.S)
                .setApiLevelForCode(AndroidApiLevel.S)
                .build()));
  }

  private void invokeBootstrapMethod(
      ConstantDynamicReference reference, ImmutableList.Builder<CfInstruction> instructions) {
    assert reference.getBootstrapMethod().type.isInvokeStatic();

    // TODO(b/178172809): Use MethodHandle.invokeWithArguments if supported.
    DexMethodHandle bootstrapMethodHandle = reference.getBootstrapMethod();
    DexMethod bootstrapMethodReference = bootstrapMethodHandle.asMethod();
    MethodResolutionResult resolution =
        appView
            .appInfoForDesugaring()
            .resolveMethod(bootstrapMethodReference, bootstrapMethodHandle.isInterface);
    if (resolution.isFailedResolution()) {
      // TODO(b/178172809): Generate code which throws ICCE.
    }
    SingleResolutionResult result = resolution.asSingleResolution();
    assert result.getResolvedMethod().isStatic();
    assert result.getResolvedHolder().isProgramClass();
    instructions.add(new CfConstNull());
    instructions.add(new CfConstString(reference.getName()));
    instructions.add(new CfConstClass(reference.getType()));
    instructions.add(new CfInvoke(INVOKESTATIC, bootstrapMethodReference, false));
    instructions.add(new CfCheckCast(reference.getType()));

    // Ensure that the bootstrap method is accessible from the generated class.
    MethodAccessFlags flags = result.getResolvedMethod().getAccessFlags();
    flags.unsetPrivate();
    flags.setPublic();
  }

  private CfCode generateGetterCode(SyntheticProgramClassBuilder builder) {
    int maxStack = 3;
    int maxLocals = 2;
    ImmutableList<CfCode.LocalVariableInfo> localVariables = ImmutableList.of();
    ImmutableList.Builder<CfInstruction> instructions = ImmutableList.builder();

    CfLabel initializedTrue = new CfLabel();
    CfLabel initializedTrueSecond = new CfLabel();
    CfLabel tryCatchStart = new CfLabel();
    CfLabel tryCatchEnd = new CfLabel();
    CfLabel tryCatchTarget = new CfLabel();
    CfLabel tryCatchEndFinally = new CfLabel();

    instructions.add(new CfFieldInstruction(GETSTATIC, initializedValueField));
    instructions.add(new CfIf(If.Type.NE, ValueType.INT, initializedTrue));

    instructions.add(new CfConstClass(builder.getType()));
    instructions.add(new CfStackInstruction(Opcode.Dup));
    instructions.add(new CfStore(ValueType.OBJECT, 0));
    instructions.add(new CfMonitor(Monitor.Type.ENTER));
    instructions.add(tryCatchStart);

    instructions.add(new CfFieldInstruction(GETSTATIC, initializedValueField));
    instructions.add(new CfIf(If.Type.NE, ValueType.INT, initializedTrueSecond));

    invokeBootstrapMethod(reference, instructions);
    instructions.add(new CfFieldInstruction(PUTSTATIC, constantValueField));
    instructions.add(new CfConstNumber(1, ValueType.INT));
    instructions.add(new CfFieldInstruction(PUTSTATIC, initializedValueField));

    instructions.add(initializedTrueSecond);
    instructions.add(
        new CfFrame(
            ImmutableInt2ReferenceSortedMap.of(
                new int[] {0},
                new FrameType[] {FrameType.initialized(builder.getFactory().objectType)}),
            ImmutableDeque.of()));
    instructions.add(new CfLoad(ValueType.OBJECT, 0));
    instructions.add(new CfMonitor(Monitor.Type.EXIT));
    instructions.add(tryCatchEnd);
    instructions.add(new CfGoto(initializedTrue));

    instructions.add(tryCatchTarget);
    instructions.add(
        new CfFrame(
            ImmutableInt2ReferenceSortedMap.of(
                new int[] {0},
                new FrameType[] {FrameType.initialized(builder.getFactory().objectType)}),
            ImmutableDeque.of(FrameType.initialized(builder.getFactory().throwableType))));
    instructions.add(new CfStore(ValueType.OBJECT, 1));
    instructions.add(new CfLoad(ValueType.OBJECT, 0));
    instructions.add(new CfMonitor(Monitor.Type.EXIT));
    instructions.add(tryCatchEndFinally);
    instructions.add(new CfLoad(ValueType.OBJECT, 1));
    instructions.add(new CfThrow());

    instructions.add(initializedTrue);
    instructions.add(new CfFrame(ImmutableInt2ReferenceSortedMap.empty(), ImmutableDeque.of()));
    instructions.add(new CfFieldInstruction(GETSTATIC, constantValueField));
    instructions.add(new CfReturn(ValueType.OBJECT));

    List<CfTryCatch> tryCatchRanges =
        ImmutableList.of(
            new CfTryCatch(
                tryCatchStart,
                tryCatchEnd,
                ImmutableList.of(builder.getFactory().throwableType),
                ImmutableList.of(tryCatchTarget)),
            new CfTryCatch(
                tryCatchTarget,
                tryCatchEndFinally,
                ImmutableList.of(builder.getFactory().throwableType),
                ImmutableList.of(tryCatchTarget)));
    return new CfCode(
        builder.getType(),
        maxStack,
        maxLocals,
        instructions.build(),
        tryCatchRanges,
        localVariables);
  }

  public final DexProgramClass getConstantDynamicProgramClass() {
    assert clazz != null;
    return clazz;
  }

  public void setClass(DexProgramClass clazz) {
    assert this.clazz == null;
    assert clazz != null;
    this.clazz = clazz;
  }
}
