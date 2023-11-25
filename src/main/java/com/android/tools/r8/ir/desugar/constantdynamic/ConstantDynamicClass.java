// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.constantdynamic;

import static com.android.tools.r8.ir.desugar.constantdynamic.ConstantDynamicClass.Behaviour.CACHE_CONSTANT;
import static com.android.tools.r8.ir.desugar.constantdynamic.ConstantDynamicClass.Behaviour.THROW_ICCE;
import static com.android.tools.r8.ir.desugar.constantdynamic.ConstantDynamicClass.Behaviour.THROW_NSME;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;

import com.android.tools.r8.cf.code.CfCheckCast;
import com.android.tools.r8.cf.code.CfConstClass;
import com.android.tools.r8.cf.code.CfConstDynamic;
import com.android.tools.r8.cf.code.CfConstNull;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfFrame;
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
import com.android.tools.r8.cf.code.CfStaticFieldRead;
import com.android.tools.r8.cf.code.CfStaticFieldWrite;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.cf.code.CfThrow;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
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
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.MonitorType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.desugar.FreshLocalProvider;
import com.android.tools.r8.ir.desugar.LocalStackAllocator;
import com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringSyntheticHelper;
import com.android.tools.r8.ir.optimize.UtilityMethodsForCodeOptimizations;
import com.android.tools.r8.ir.optimize.UtilityMethodsForCodeOptimizations.MethodSynthesizerConsumer;
import com.android.tools.r8.ir.optimize.UtilityMethodsForCodeOptimizations.UtilityMethodForCodeOptimizations;
import com.android.tools.r8.synthesis.SyntheticProgramClassBuilder;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ListUtils;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import org.objectweb.asm.Opcodes;

public class ConstantDynamicClass {
  enum Behaviour {
    CACHE_CONSTANT,
    THROW_NSME,
    THROW_ICCE
  }

  public static final String INITIALIZED_FIELD_NAME = "INITIALIZED";
  public static final String CONST_FIELD_NAME = "CONST";

  private final AppView<?> appView;
  public final ConstantDynamicReference reference;
  public final DexField initializedValueField;
  public final DexField constantValueField;
  private final DexMethod getConstMethod;
  private final Behaviour behaviour;
  private DexMethod bootstrapMethodReference;
  private DexMethod finalBootstrapMethodReference;
  private boolean isFinalBootstrapMethodReferenceOnInterface;

  // Considered final but is set after due to circularity in allocation.
  private DexProgramClass clazz = null;

  @SuppressWarnings("ReferenceEquality")
  public ConstantDynamicClass(
      SyntheticProgramClassBuilder builder,
      AppView<?> appView,
      ProgramMethod context,
      CfConstDynamic constantDynamic) {
    DexItemFactory factory = appView.dexItemFactory();
    this.appView = appView;
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

    DexMethodHandle bootstrapMethodHandle = reference.getBootstrapMethod();
    bootstrapMethodReference = bootstrapMethodHandle.asMethod();
    MethodResolutionResult resolution =
        appView
            .appInfoForDesugaring()
            .resolveMethodLegacy(bootstrapMethodReference, bootstrapMethodHandle.isInterface);
    if (resolution.isSingleResolution()
        && resolution.asSingleResolution().getResolvedMethod().isStatic()) {
      SingleResolutionResult<?> result = resolution.asSingleResolution();
      if (bootstrapMethodHandle.isInterface
          && appView.options().isInterfaceMethodDesugaringEnabled()) {
        bootstrapMethodReference =
            bootstrapMethodReference.withHolder(
                InterfaceDesugaringSyntheticHelper.getCompanionClassType(
                    bootstrapMethodReference.getHolderType(), factory),
                factory);
        isFinalBootstrapMethodReferenceOnInterface = false;
      } else {
        assert bootstrapMethodReference.getHolderType() == resolution.getResolvedHolder().getType();
        isFinalBootstrapMethodReferenceOnInterface = bootstrapMethodHandle.isInterface;
      }
      if (shouldRewriteBootstrapMethodSignature()) {
        // The bootstrap method will have its signature modified to have type Object as its first
        // argument.
        this.finalBootstrapMethodReference =
            factory.createMethod(
                bootstrapMethodReference.getHolderType(),
                factory.createProto(
                    bootstrapMethodReference.getReturnType(),
                    factory.objectType,
                    factory.stringType,
                    factory.classType),
                bootstrapMethodReference.getName());
      } else {
        this.finalBootstrapMethodReference = bootstrapMethodReference;
        // Ensure that the bootstrap method is accessible from the generated class.
        DexEncodedMethod bootstrapMethodImpl = result.getResolvedMethod();
        MethodAccessFlags flags = bootstrapMethodImpl.getAccessFlags();
        flags.unsetPrivate();
        flags.setPublic();
      }

      behaviour = CACHE_CONSTANT;

      synthesizeConstantDynamicClass(builder);
    } else {
      // Unconditionally throw as the RI.
      behaviour =
          resolution.isNoSuchMethodErrorResult(
                  context.getContextClass(), appView, appView.appInfoForDesugaring())
              ? THROW_NSME
              : THROW_ICCE;
    }
  }

  private boolean shouldRewriteBootstrapMethodSignature() {
    // TODO(b/210485236): Check for references to the bootstrap method outside of dynamic constant.
    return !appView.enableWholeProgramOptimizations()
        && appView.options().getMinApiLevel().isLessThan(AndroidApiLevel.O);
  }

  public Collection<CfInstruction> desugarConstDynamicInstruction(
      CfConstDynamic invoke,
      FreshLocalProvider freshLocalProvider,
      LocalStackAllocator localStackAllocator,
      ConstantDynamicDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext) {
    assert invoke.getReference().equals(reference);
    if (behaviour == CACHE_CONSTANT) {
      return ImmutableList.of(new CfInvoke(Opcodes.INVOKESTATIC, getConstMethod, false));
    }
    return desugarToThrow(
        behaviour == THROW_NSME
            ? UtilityMethodsForCodeOptimizations::synthesizeThrowNoSuchMethodErrorMethod
            : UtilityMethodsForCodeOptimizations::synthesizeThrowIncompatibleClassChangeErrorMethod,
        eventConsumer,
        methodProcessingContext);
  }

  private Collection<CfInstruction> desugarToThrow(
      MethodSynthesizerConsumer methodSynthesizerConsumer,
      ConstantDynamicDesugaringEventConsumer eventConsumer,
      MethodProcessingContext methodProcessingContext) {
    UtilityMethodForCodeOptimizations throwMethod =
        methodSynthesizerConsumer.synthesizeMethod(appView, eventConsumer, methodProcessingContext);
    ProgramMethod throwProgramMethod = throwMethod.uncheckedGetMethod();
    return ImmutableList.of(new CfInvoke(INVOKESTATIC, throwProgramMethod.getReference(), false));
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
            DexEncodedField.syntheticBuilder()
                .setField(this.initializedValueField)
                .setAccessFlags(FieldAccessFlags.createPrivateStaticSynthetic())
                .disableAndroidApiLevelCheck()
                .build(),
            DexEncodedField.syntheticBuilder()
                .setField(this.constantValueField)
                .setAccessFlags(FieldAccessFlags.createPrivateStaticSynthetic())
                .disableAndroidApiLevelCheck()
                .build()));
  }

  private void synthesizeDirectMethods(SyntheticProgramClassBuilder builder) {
    builder.setDirectMethods(
        ImmutableList.of(
            DexEncodedMethod.syntheticBuilder()
                .setMethod(getConstMethod)
                .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                .setCode(generateGetterCode(builder))
                .disableAndroidApiLevelCheck()
                .build()));
  }

  private void invokeBootstrapMethod(ImmutableList.Builder<CfInstruction> instructions) {
    assert reference.getBootstrapMethod().type.isInvokeStatic();
    // TODO(b/178172809): Use MethodHandle.invokeWithArguments if supported.
    instructions.add(CfConstNull.INSTANCE);
    instructions.add(new CfConstString(reference.getName()));
    instructions.add(new CfConstClass(reference.getType()));
    instructions.add(
        new CfInvoke(
            INVOKESTATIC,
            finalBootstrapMethodReference,
            isFinalBootstrapMethodReferenceOnInterface));
    instructions.add(new CfCheckCast(reference.getType()));
  }

  private CfCode generateGetterCode(SyntheticProgramClassBuilder builder) {
    // TODO(b/178172809): Use MethodHandle.invokeWithArguments if supported.
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

    instructions.add(new CfStaticFieldRead(initializedValueField));
    instructions.add(new CfIf(IfType.NE, ValueType.INT, initializedTrue));

    instructions.add(new CfConstClass(builder.getType()));
    instructions.add(CfStackInstruction.DUP);
    instructions.add(CfStore.ASTORE_0);
    instructions.add(CfMonitor.ENTER);
    instructions.add(tryCatchStart);

    instructions.add(new CfStaticFieldRead(initializedValueField));
    instructions.add(new CfIf(IfType.NE, ValueType.INT, initializedTrueSecond));

    invokeBootstrapMethod(instructions);
    instructions.add(new CfStaticFieldWrite(constantValueField));
    instructions.add(CfConstNumber.ICONST_1);
    instructions.add(new CfStaticFieldWrite(initializedValueField));

    instructions.add(initializedTrueSecond);
    instructions.add(
        CfFrame.builder()
            .appendLocal(FrameType.initializedNonNullReference(builder.getFactory().objectType))
            .build());
    instructions.add(CfLoad.ALOAD_0);
    instructions.add(CfMonitor.EXIT);
    instructions.add(tryCatchEnd);
    instructions.add(new CfGoto(initializedTrue));

    instructions.add(tryCatchTarget);
    instructions.add(
        CfFrame.builder()
            .appendLocal(FrameType.initializedNonNullReference(builder.getFactory().objectType))
            .push(FrameType.initializedNonNullReference(builder.getFactory().throwableType))
            .build());
    instructions.add(CfStore.ASTORE_1);
    instructions.add(CfLoad.ALOAD_0);
    instructions.add(CfMonitor.EXIT);
    instructions.add(tryCatchEndFinally);
    instructions.add(CfLoad.ALOAD_1);
    instructions.add(CfThrow.INSTANCE);

    instructions.add(initializedTrue);
    instructions.add(new CfFrame());
    instructions.add(new CfStaticFieldRead(constantValueField));
    instructions.add(CfReturn.ARETURN);

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

  public void rewriteBootstrapMethodSignatureIfNeeded(
      ConstantDynamicDesugaringEventConsumer eventConsumer) {
    if (!shouldRewriteBootstrapMethodSignature() || behaviour != CACHE_CONSTANT) {
      return;
    }
    DexProgramClass bootstrapMethodHolder =
        appView.definitionFor(bootstrapMethodReference.getHolderType()).asProgramClass();
    DexEncodedMethod finalDefinition =
        bootstrapMethodHolder
            .getMethodCollection()
            .replaceDirectMethod(
                bootstrapMethodReference,
                encodedMethod -> {
                  MethodAccessFlags newAccessFlags = encodedMethod.accessFlags.copy();
                  // Ensure that the bootstrap method is accessible from the generated class.
                  newAccessFlags.unsetPrivate();
                  newAccessFlags.setPublic();
                  DexEncodedMethod newMethod =
                      DexEncodedMethod.syntheticBuilder()
                          .setMethod(finalBootstrapMethodReference)
                          .setAccessFlags(newAccessFlags)
                          .setGenericSignature(encodedMethod.getGenericSignature())
                          .setAnnotations(encodedMethod.annotations())
                          .setParameterAnnotations(encodedMethod.parameterAnnotationsList)
                          .setCode(adaptCode(encodedMethod))
                          .setApiLevelForDefinition(encodedMethod.getApiLevelForDefinition())
                          .setApiLevelForCode(encodedMethod.getApiLevelForCode())
                          .build();
                  newMethod.copyMetadata(appView, encodedMethod);
                  return newMethod;
                });
    ProgramMethod finalMethod;
    if (finalDefinition != null) {
      // Since we've copied the code object from an existing method, the code should already be
      // processed, and thus we don't need to schedule it for processing in D8.
      assert !appView.options().isGeneratingClassFiles() || finalDefinition.getCode().isCfCode();
      assert !appView.options().isGeneratingDex() || finalDefinition.getCode().isDexCode();
      finalMethod = finalDefinition.asProgramMethod(bootstrapMethodHolder);
      eventConsumer.acceptConstantDynamicRewrittenBootstrapMethod(
          finalMethod, bootstrapMethodReference);
    } else {
      finalMethod = bootstrapMethodHolder.lookupProgramMethod(finalBootstrapMethodReference);
    }
    // The method might already have been moved by another dynamic constant targeting it.
    // If so, it must be defined on the holder.
    assert finalMethod != null;
    assert finalMethod.getDefinition().isPublicMethod();
  }

  @SuppressWarnings("ReferenceEquality")
  private DexType mapLookupTypeToObject(DexType type) {
    return type == appView.dexItemFactory().lookupType ? appView.dexItemFactory().objectType : type;
  }

  private Code adaptCode(DexEncodedMethod method) {
    assert behaviour == CACHE_CONSTANT;
    if (method.getCode().isDexCode()) {
      return method.getCode();
    }
    CfCode code = method.getCode().asCfCode();
    List<CfInstruction> newInstructions =
        ListUtils.mapOrElse(
            code.getInstructions(),
            instruction ->
                instruction.isFrame()
                    ? instruction.asFrame().mapReferenceTypes(this::mapLookupTypeToObject)
                    : instruction);
    return code.getInstructions() != newInstructions
        ? new CfCode(
            method.getHolderType(),
            code.getMaxStack(),
            code.getMaxLocals(),
            newInstructions,
            code.getTryCatchRanges(),
            code.getLocalVariables())
        : code;
  }
}
