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
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue.DexValueNull;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.synthesis.SyntheticProgramClassBuilder;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;

public class ConstantDynamicClass {
  public static final String CONDY_CONST_FIELD_NAME = "CONST";

  final AppView<?> appView;
  final ConstantDynamicInstructionDesugaring desugaring;
  private final DexType accessedFrom;
  public ConstantDynamicReference reference;
  public final DexField constantValueField;
  final DexMethod classConstructor;
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
            builder.getType(),
            constantDynamic.getType(),
            factory.createString(CONDY_CONST_FIELD_NAME));
    this.classConstructor =
        factory.createMethod(
            builder.getType(),
            factory.createProto(factory.voidType),
            factory.classConstructorMethodName);
    this.getConstMethod =
        factory.createMethod(
            builder.getType(),
            factory.createProto(constantDynamic.getType()),
            factory.createString("get"));

    synthesizeConstantDynamicClass(builder);
  }

  /*
    // TODO(b/178172809): Don't use <clinit> to synchronize constant creation.
    // Simple one class per. constant using <clinit> to synchronize constant creation
    // generated with a pattern like this:

    class ExternalSyntheticXXX {
       public static <constant type> CONST =
           (<constant type>) bootstrapMethod(null, "constant name", <constant type>)

       public static <constant type> get() {
         return CONST;
       }
     }

  */
  private void synthesizeConstantDynamicClass(SyntheticProgramClassBuilder builder) {
    synthesizeStaticFields(builder);
    synthesizeDirectMethods(builder);
  }

  private void synthesizeStaticFields(SyntheticProgramClassBuilder builder) {
    // Create static field for the constant.
    boolean deprecated = false;
    boolean d8R8Synthesized = true;
    builder.setStaticFields(
        ImmutableList.of(
            new DexEncodedField(
                this.constantValueField,
                FieldAccessFlags.fromSharedAccessFlags(
                    Constants.ACC_PRIVATE
                        | Constants.ACC_FINAL
                        | Constants.ACC_SYNTHETIC
                        | Constants.ACC_STATIC),
                FieldTypeSignature.noSignature(),
                DexAnnotationSet.empty(),
                DexValueNull.NULL,
                deprecated,
                d8R8Synthesized,
                // The api level is computed when tracing.
                AndroidApiLevel.minApiLevelIfEnabledOrUnknown(appView))));
  }

  private void synthesizeDirectMethods(SyntheticProgramClassBuilder builder) {

    List<DexEncodedMethod> methods = new ArrayList<>(2);
    methods.add(
        DexEncodedMethod.builder()
            .setMethod(classConstructor)
            .setAccessFlags(MethodAccessFlags.createForClassInitializer())
            .setCode(generateClassInitializerCode(builder.getType()))
            .setD8R8Synthesized()
            .setApiLevelForDefinition(AndroidApiLevel.S)
            .setApiLevelForCode(AndroidApiLevel.S)
            .build());
    methods.add(
        DexEncodedMethod.builder()
            .setMethod(getConstMethod)
            .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
            .setCode(generateGetterCode(builder.getType()))
            .setD8R8Synthesized()
            .setApiLevelForDefinition(AndroidApiLevel.S)
            .setApiLevelForCode(AndroidApiLevel.S)
            .build());
    builder.setDirectMethods(methods);
  }

  private CfCode generateClassInitializerCode(DexType holder) {
    int maxStack = 3;
    int maxLocals = 0;
    ImmutableList<CfTryCatch> tryCatchRanges = ImmutableList.of();
    ImmutableList<CfCode.LocalVariableInfo> localVariables = ImmutableList.of();
    ImmutableList.Builder<CfInstruction> instructions = ImmutableList.builder();
    invokeBootstrapMethod(reference, instructions);
    instructions.add(new CfFieldInstruction(PUTSTATIC, constantValueField));
    instructions.add(new CfReturnVoid());
    return new CfCode(
        holder, maxStack, maxLocals, instructions.build(), tryCatchRanges, localVariables);
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

  private CfCode generateGetterCode(DexType holder) {
    int maxStack = 1;
    int maxLocals = 0;
    ImmutableList<CfTryCatch> tryCatchRanges = ImmutableList.of();
    ImmutableList<CfCode.LocalVariableInfo> localVariables = ImmutableList.of();
    ImmutableList.Builder<CfInstruction> instructions = ImmutableList.builder();
    instructions.add(new CfFieldInstruction(GETSTATIC, constantValueField));
    instructions.add(new CfReturn(ValueType.OBJECT));
    return new CfCode(
        holder, maxStack, maxLocals, instructions.build(), tryCatchRanges, localVariables);
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
