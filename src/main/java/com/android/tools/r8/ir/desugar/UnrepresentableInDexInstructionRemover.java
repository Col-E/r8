// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import static com.android.tools.r8.ir.optimize.UtilityMethodsForCodeOptimizations.synthesizeThrowRuntimeExceptionWithMessageMethod;

import com.android.tools.r8.cf.code.CfConstDynamic;
import com.android.tools.r8.cf.code.CfConstMethodHandle;
import com.android.tools.r8.cf.code.CfConstMethodType;
import com.android.tools.r8.cf.code.CfConstNull;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfInvokeDynamic;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.errors.UnsupportedConstDynamicDiagnostic;
import com.android.tools.r8.errors.UnsupportedConstMethodHandleDiagnostic;
import com.android.tools.r8.errors.UnsupportedConstMethodTypeDiagnostic;
import com.android.tools.r8.errors.UnsupportedFeatureDiagnostic;
import com.android.tools.r8.errors.UnsupportedInvokeCustomDiagnostic;
import com.android.tools.r8.errors.UnsupportedInvokePolymorphicMethodHandleDiagnostic;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.optimize.UtilityMethodsForCodeOptimizations.UtilityMethodForCodeOptimizations;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.MethodPosition;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.SetUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.Opcodes;

/**
 * Non desugared invoke-dynamic instructions as well as MethodHandle.invokeX instructions cannot be
 * represented below O API level. Desugar them into throwing stubs to allow compilation to proceed
 * under the assumption that the code is dead code.
 */
public class UnrepresentableInDexInstructionRemover implements CfInstructionDesugaring {

  private abstract static class InstructionMatcher {
    final AppView<?> appView;
    final String descriptor;
    final AndroidApiLevel supportedApiLevel;
    // TODO(b/237250957): Using ConcurrentHashMap.newKeySet() causes failures on:
    //  HelloWorldCompiledOnArtTest.testHelloCompiledWithX8Dex[Y, api:21, spec: JDK8, D8_L8DEBUG]
    final Set<DexMethod> reported = SetUtils.newConcurrentHashSet();

    InstructionMatcher(AppView<?> appView, String descriptor, AndroidApiLevel supportedApiLevel) {
      this.appView = appView;
      this.descriptor = descriptor;
      this.supportedApiLevel = supportedApiLevel;
    }

    // Rewrite implementation for each instruction case.
    abstract DesugarDescription compute(CfInstruction instruction);

    abstract UnsupportedFeatureDiagnostic makeDiagnostic(Origin origin, Position position);

    // Helpers

    void report(ProgramMethod context) {
      if (reported.add(context.getReference())) {
        UnsupportedFeatureDiagnostic diagnostic =
            makeDiagnostic(context.getOrigin(), MethodPosition.create(context));
        assert (diagnostic.getSupportedApiLevel() == -1 && supportedApiLevel == null)
            || (diagnostic.getSupportedApiLevel() == supportedApiLevel.getLevel());
        appView.reporter().warning(diagnostic);
      }
    }

    void invokeThrowingStub(
        MethodProcessingContext methodProcessingContext,
        CfInstructionDesugaringEventConsumer eventConsumer,
        ImmutableList.Builder<CfInstruction> builder) {
      UtilityMethodForCodeOptimizations throwUtility =
          synthesizeThrowRuntimeExceptionWithMessageMethod(
              appView, eventConsumer, methodProcessingContext);
      ProgramMethod throwMethod = throwUtility.uncheckedGetMethod();
      builder.add(
          createMessageString(),
          new CfInvoke(Opcodes.INVOKESTATIC, throwMethod.getReference(), false),
          new CfStackInstruction(Opcode.Pop));
    }

    CfConstString createMessageString() {
      return new CfConstString(
          appView
              .dexItemFactory()
              .createString(
                  "Instruction is unrepresentable in DEX "
                      + appView.options().getMinApiLevel().getDexVersion()
                      + ": "
                      + descriptor));
    }

    @SuppressWarnings("BadImport")
    static void pop(DexType type, Builder<CfInstruction> builder) {
      assert !type.isVoidType();
      builder.add(new CfStackInstruction(type.isWideType() ? Opcode.Pop2 : Opcode.Pop));
    }

    @SuppressWarnings("BadImport")
    static void pop(DexProto proto, Builder<CfInstruction> builder) {
      // Pop arguments in reverse order from the stack.
      proto.getParameters().forEachReverse(t -> pop(t, builder));
    }

    @SuppressWarnings("BadImport")
    static Builder<CfInstruction> pushReturnValue(DexType type, Builder<CfInstruction> builder) {
      if (!type.isVoidType()) {
        builder.add(createDefaultValueForType(type));
      }
      return builder;
    }

    static CfInstruction createDefaultValueForType(DexType type) {
      assert !type.isVoidType();
      if (type.isPrimitiveType()) {
        return new CfConstNumber(0, ValueType.fromDexType(type));
      }
      assert type.isReferenceType();
      return new CfConstNull();
    }
  }

  private static class InvokeDynamicMatcher extends InstructionMatcher {

    @SuppressWarnings("BadImport")
    static void addIfNeeded(AppView<?> appView, Builder<InstructionMatcher> builder) {
      InternalOptions options = appView.options();
      if (!options.canUseInvokeCustom()) {
        builder.add(new InvokeDynamicMatcher(appView));
      }
    }

    InvokeDynamicMatcher(AppView<?> appView) {
      super(appView, "invoke-dynamic", InternalOptions.invokeCustomApiLevel());
    }

    @Override
    UnsupportedFeatureDiagnostic makeDiagnostic(Origin origin, Position position) {
      return new UnsupportedInvokeCustomDiagnostic(origin, position);
    }

    @Override
    @SuppressWarnings("BadImport")
    DesugarDescription compute(CfInstruction instruction) {
      CfInvokeDynamic invokeDynamic = instruction.asInvokeDynamic();
      if (invokeDynamic == null) {
        return null;
      }
      return DesugarDescription.builder()
          .setDesugarRewrite(
              (freshLocalProvider,
                  localStackAllocator,
                  eventConsumer,
                  context,
                  methodProcessingContext,
                  desugaringCollection,
                  dexItemFactory) -> {
                report(context);
                Builder<CfInstruction> replacement = ImmutableList.builder();
                DexCallSite callSite = invokeDynamic.getCallSite();
                pop(callSite.getMethodProto(), replacement);
                localStackAllocator.allocateLocalStack(1);
                invokeThrowingStub(methodProcessingContext, eventConsumer, replacement);
                pushReturnValue(callSite.getMethodProto().getReturnType(), replacement);
                return replacement.build();
              })
          .build();
    }
  }

  private static class InvokePolymorphicMatcher extends InstructionMatcher {

    @SuppressWarnings("BadImport")
    static void addIfNeeded(AppView<?> appView, Builder<InstructionMatcher> builder) {
      InternalOptions options = appView.options();
      if (!options.canUseInvokePolymorphicOnMethodHandle()) {
        builder.add(new InvokePolymorphicMatcher(appView));
      }
    }

    InvokePolymorphicMatcher(AppView<?> appView) {
      super(
          appView, "invoke-polymorphic", InternalOptions.invokePolymorphicOnMethodHandleApiLevel());
    }

    boolean isPolymorphicInvoke(CfInvoke invoke) {
      return appView.dexItemFactory().polymorphicMethods.isPolymorphicInvoke(invoke.getMethod());
    }

    @Override
    UnsupportedFeatureDiagnostic makeDiagnostic(Origin origin, Position position) {
      return new UnsupportedInvokePolymorphicMethodHandleDiagnostic(origin, position);
    }

    @Override
    @SuppressWarnings("BadImport")
    DesugarDescription compute(CfInstruction instruction) {
      CfInvoke invoke = instruction.asInvoke();
      if (invoke == null || !isPolymorphicInvoke(invoke)) {
        return null;
      }
      return DesugarDescription.builder()
          .setDesugarRewrite(
              (freshLocalProvider,
                  localStackAllocator,
                  eventConsumer,
                  context,
                  methodProcessingContext,
                  desugaringCollection,
                  dexItemFactory) -> {
                report(context);
                Builder<CfInstruction> replacement = ImmutableList.builder();
                pop(invoke.getMethod().getProto(), replacement);
                if (!invoke.isInvokeStatic()) {
                  pop(dexItemFactory.objectType, replacement);
                }
                localStackAllocator.allocateLocalStack(1);
                invokeThrowingStub(methodProcessingContext, eventConsumer, replacement);
                pushReturnValue(invoke.getMethod().getReturnType(), replacement);
                return replacement.build();
              })
          .build();
    }
  }

  private static class ConstMethodHandleMatcher extends InstructionMatcher {

    @SuppressWarnings("BadImport")
    static void addIfNeeded(AppView<?> appView, Builder<InstructionMatcher> builder) {
      InternalOptions options = appView.options();
      if (!options.canUseConstantMethodHandle()) {
        builder.add(new ConstMethodHandleMatcher(appView));
      }
    }

    ConstMethodHandleMatcher(AppView<?> appView) {
      super(appView, "const-method-handle", InternalOptions.constantMethodHandleApiLevel());
    }

    @Override
    UnsupportedFeatureDiagnostic makeDiagnostic(Origin origin, Position position) {
      return new UnsupportedConstMethodHandleDiagnostic(origin, position);
    }

    @Override
    @SuppressWarnings("BadImport")
    DesugarDescription compute(CfInstruction instruction) {
      if (!(instruction instanceof CfConstMethodHandle)) {
        return null;
      }
      return DesugarDescription.builder()
          .setDesugarRewrite(
              (freshLocalProvider,
                  localStackAllocator,
                  eventConsumer,
                  context,
                  methodProcessingContext,
                  desugaringCollection,
                  dexItemFactory) -> {
                report(context);
                Builder<CfInstruction> replacement = ImmutableList.builder();
                invokeThrowingStub(methodProcessingContext, eventConsumer, replacement);
                return replacement.add(new CfConstNull()).build();
              })
          .build();
    }
  }

  private static class ConstMethodTypeMatcher extends InstructionMatcher {

    @SuppressWarnings("BadImport")
    static void addIfNeeded(AppView<?> appView, Builder<InstructionMatcher> builder) {
      InternalOptions options = appView.options();
      if (!options.canUseConstantMethodType()) {
        builder.add(new ConstMethodTypeMatcher(appView));
      }
    }

    ConstMethodTypeMatcher(AppView<?> appView) {
      super(appView, "const-method-type", InternalOptions.constantMethodTypeApiLevel());
    }

    @Override
    UnsupportedFeatureDiagnostic makeDiagnostic(Origin origin, Position position) {
      return new UnsupportedConstMethodTypeDiagnostic(origin, position);
    }

    @Override
    @SuppressWarnings("BadImport")
    DesugarDescription compute(CfInstruction instruction) {
      if (!(instruction instanceof CfConstMethodType)) {
        return null;
      }
      return DesugarDescription.builder()
          .setDesugarRewrite(
              (freshLocalProvider,
                  localStackAllocator,
                  eventConsumer,
                  context,
                  methodProcessingContext,
                  desugaringCollection,
                  dexItemFactory) -> {
                report(context);
                Builder<CfInstruction> replacement = ImmutableList.builder();
                invokeThrowingStub(methodProcessingContext, eventConsumer, replacement);
                return replacement.add(new CfConstNull()).build();
              })
          .build();
    }
  }

  private static class ConstDynamicMatcher extends InstructionMatcher {

    @SuppressWarnings("BadImport")
    static void addIfNeeded(AppView<?> appView, Builder<InstructionMatcher> builder) {
      InternalOptions options = appView.options();
      if (!options.canUseConstantDynamic()) {
        builder.add(new ConstDynamicMatcher(appView));
      }
    }

    ConstDynamicMatcher(AppView<?> appView) {
      super(appView, "const-dynamic", InternalOptions.constantDynamicApiLevel());
    }

    @Override
    UnsupportedFeatureDiagnostic makeDiagnostic(Origin origin, Position position) {
      return new UnsupportedConstDynamicDiagnostic(origin, position);
    }

    @Override
    @SuppressWarnings("BadImport")
    DesugarDescription compute(CfInstruction instruction) {
      final CfConstDynamic constDynamic = instruction.asConstDynamic();
      if (constDynamic == null) {
        return null;
      }
      return DesugarDescription.builder()
          .setDesugarRewrite(
              (freshLocalProvider,
                  localStackAllocator,
                  eventConsumer,
                  context,
                  methodProcessingContext,
                  desugaringCollection,
                  dexItemFactory) -> {
                report(context);
                Builder<CfInstruction> replacement = ImmutableList.builder();
                invokeThrowingStub(methodProcessingContext, eventConsumer, replacement);
                return pushReturnValue(constDynamic.getType(), replacement).build();
              })
          .build();
    }
  }

  private final List<InstructionMatcher> matchers;

  @SuppressWarnings("BadImport")
  public UnrepresentableInDexInstructionRemover(AppView<?> appView) {
    Builder<InstructionMatcher> builder = ImmutableList.builder();
    InvokeDynamicMatcher.addIfNeeded(appView, builder);
    InvokePolymorphicMatcher.addIfNeeded(appView, builder);
    ConstMethodHandleMatcher.addIfNeeded(appView, builder);
    ConstMethodTypeMatcher.addIfNeeded(appView, builder);
    ConstDynamicMatcher.addIfNeeded(appView, builder);
    matchers = builder.build();
  }

  @Override
  public DesugarDescription compute(CfInstruction instruction, ProgramMethod context) {
    for (InstructionMatcher matcher : matchers) {
      DesugarDescription result = matcher.compute(instruction);
      if (result != null) {
        return result;
      }
    }
    return DesugarDescription.nothing();
  }
}
