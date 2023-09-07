// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.twr;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.DesugarDescription;
import com.android.tools.r8.ir.desugar.backports.BackportedMethods;
import com.android.tools.r8.synthesis.SyntheticItems.SyntheticKindSelector;
import com.google.common.collect.ImmutableList;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import org.objectweb.asm.Opcodes;

public class TwrInstructionDesugaring implements CfInstructionDesugaring {

  private final AppView<?> appView;
  private final DexItemFactory dexItemFactory;
  private final DexProto twrCloseResourceProto;
  private final DexMethod addSuppressed;
  private final DexMethod getSuppressed;

  public TwrInstructionDesugaring(AppView<?> appView) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
    this.twrCloseResourceProto =
        dexItemFactory.createProto(
            dexItemFactory.voidType, dexItemFactory.throwableType, dexItemFactory.objectType);
    this.addSuppressed = dexItemFactory.throwableMethods.addSuppressed;
    this.getSuppressed = dexItemFactory.throwableMethods.getSuppressed;
  }

  @Override
  public DesugarDescription compute(CfInstruction instruction, ProgramMethod context) {
    if (!instruction.isInvoke()) {
      return DesugarDescription.nothing();
    }
    if (isTwrCloseResourceInvoke(instruction)) {
      return rewriteTwrCloseResourceInvoke();
    }
    if (!appView.options().canUseSuppressedExceptions()) {
      if (isTwrSuppressedInvoke(instruction, addSuppressed)) {
        return rewriteTwrAddSuppressedInvoke();
      }
      if (isTwrSuppressedInvoke(instruction, getSuppressed)) {
        return rewriteTwrGetSuppressedInvoke();
      }
    }
    return DesugarDescription.nothing();
  }

  private DesugarDescription rewriteTwrAddSuppressedInvoke() {
    DexItemFactory factory = appView.dexItemFactory();
    DexProto proto =
        factory.createProto(factory.voidType, factory.throwableType, factory.throwableType);
    return DesugarDescription.builder()
        .setDesugarRewrite(
            (freshLocalProvider,
                localStackAllocator,
                desugaringInfo,
                eventConsumer,
                context,
                methodProcessingContext,
                desugaringCollection,
                dexItemFactory) ->
                createAndCallSyntheticMethod(
                    kinds -> kinds.BACKPORT,
                    proto,
                    BackportedMethods::ThrowableMethods_addSuppressed,
                    methodProcessingContext,
                    eventConsumer::acceptBackportedMethod,
                    methodProcessingContext.getMethodContext()))
        .build();
  }

  private DesugarDescription rewriteTwrGetSuppressedInvoke() {
    DexItemFactory factory = appView.dexItemFactory();
    DexProto proto =
        factory.createProto(
            factory.createArrayType(1, factory.throwableType), factory.throwableType);
    return DesugarDescription.builder()
        .setDesugarRewrite(
            (freshLocalProvider,
                localStackAllocator,
                desugaringInfo,
                eventConsumer,
                context,
                methodProcessingContext,
                desugaringCollection,
                dexItemFactory) ->
                createAndCallSyntheticMethod(
                    kinds -> kinds.BACKPORT,
                    proto,
                    BackportedMethods::ThrowableMethods_getSuppressed,
                    methodProcessingContext,
                    eventConsumer::acceptBackportedMethod,
                    methodProcessingContext.getMethodContext()))
        .build();
  }

  private DesugarDescription rewriteTwrCloseResourceInvoke() {
    // Synthesize a new method.
    return DesugarDescription.builder()
        .setDesugarRewrite(
            (freshLocalProvider,
                localStackAllocator,
                desugaringInfo,
                eventConsumer,
                context,
                methodProcessingContext,
                desugaringCollection,
                dexItemFactory) ->
                createAndCallSyntheticMethod(
                    kinds -> kinds.TWR_CLOSE_RESOURCE,
                    twrCloseResourceProto,
                    BackportedMethods::CloseResourceMethod_closeResourceImpl,
                    methodProcessingContext,
                    eventConsumer::acceptTwrCloseResourceMethod,
                    methodProcessingContext.getMethodContext()))
        .build();
  }

  private ImmutableList<CfInstruction> createAndCallSyntheticMethod(
      SyntheticKindSelector kindSelector,
      DexProto proto,
      BiFunction<DexItemFactory, DexMethod, CfCode> generator,
      MethodProcessingContext methodProcessingContext,
      BiConsumer<ProgramMethod, ProgramMethod> eventConsumerCallback,
      ProgramMethod context) {
    ProgramMethod method =
        appView
            .getSyntheticItems()
            .createMethod(
                kindSelector,
                methodProcessingContext.createUniqueContext(),
                appView,
                builder ->
                    builder
                        // Will be traced by the enqueuer.
                        .disableAndroidApiLevelCheck()
                        .setProto(proto)
                        .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                        .setCode(
                            methodSig -> generator.apply(appView.dexItemFactory(), methodSig)));
    eventConsumerCallback.accept(method, context);
    return ImmutableList.of(new CfInvoke(Opcodes.INVOKESTATIC, method.getReference(), false));
  }

  private boolean isTwrSuppressedInvoke(CfInstruction instruction, DexMethod suppressed) {
    return instruction.isInvoke()
        && matchesMethodOfThrowable(instruction.asInvoke().getMethod(), suppressed);
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean matchesMethodOfThrowable(DexMethod invoked, DexMethod expected) {
    return invoked.name == expected.name
        && invoked.proto == expected.proto
        && isSubtypeOfThrowable(invoked.holder);
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean isSubtypeOfThrowable(DexType type) {
    while (type != null && type != dexItemFactory.objectType) {
      if (type == dexItemFactory.throwableType) {
        return true;
      }
      DexClass dexClass = appView.definitionFor(type);
      if (dexClass == null) {
        throw new CompilationError(
            "Class or interface "
                + type.toSourceString()
                + " required for desugaring of try-with-resources is not found.");
      }
      type = dexClass.superType;
    }
    return false;
  }

  private boolean isTwrCloseResourceInvoke(CfInstruction instruction) {
    return instruction.isInvokeStatic()
        && isTwrCloseResourceMethod(instruction.asInvoke().getMethod());
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean isTwrCloseResourceMethod(DexMethod method) {
    return method.name == dexItemFactory.twrCloseResourceMethodName
        && method.proto == dexItemFactory.twrCloseResourceMethodProto;
  }
}
