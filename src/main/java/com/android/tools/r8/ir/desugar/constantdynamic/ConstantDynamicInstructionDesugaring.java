// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.constantdynamic;

import com.android.tools.r8.cf.code.CfConstDynamic;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.errors.ConstantDynamicDesugarDiagnostic;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.DesugarDescription;
import com.android.tools.r8.ir.desugar.FreshLocalProvider;
import com.android.tools.r8.ir.desugar.LocalStackAllocator;
import com.android.tools.r8.position.MethodPosition;
import com.android.tools.r8.utils.Box;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConstantDynamicInstructionDesugaring implements CfInstructionDesugaring {

  private final AppView<?> appView;
  private final Map<DexType, Map<ConstantDynamicReference, ConstantDynamicClass>>
      dynamicConstantSyntheticsPerClass = new ConcurrentHashMap<>();

  public ConstantDynamicInstructionDesugaring(AppView<?> appView) {
    this.appView = appView;
  }

  private DesugarDescription report(String message, ProgramMethod context) {
    return DesugarDescription.builder()
        .addScanEffect(
            () ->
                appView
                    .reporter()
                    .error(
                        new ConstantDynamicDesugarDiagnostic(
                            context.getOrigin(), MethodPosition.create(context), message)))
        .build();
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public DesugarDescription compute(CfInstruction instruction, ProgramMethod context) {
    if (!instruction.isConstDynamic()) {
      return DesugarDescription.nothing();
    }
    CfConstDynamic constDynamic = instruction.asConstDynamic();
    if (!constDynamic.getBootstrapMethodArguments().isEmpty()) {
      // TODO(b/178172809): Handle bootstrap arguments.
      return report("Unsupported dynamic constant (has arguments to bootstrap method)", context);
    }
    if (!constDynamic.getBootstrapMethod().type.isInvokeStatic()) {
      return report("Unsupported dynamic constant (not invoke static)", context);
    }
    DexItemFactory factory = appView.dexItemFactory();
    DexMethod bootstrapMethod = constDynamic.getBootstrapMethod().asMethod();
    DexType holder = bootstrapMethod.getHolderType();
    if (holder == factory.constantBootstrapsType) {
      return report("Unsupported dynamic constant (runtime provided bootstrap method)", context);
    }
    if (holder != context.getHolderType()) {
      return report("Unsupported dynamic constant (different owner)", context);
    }
    if (bootstrapMethod.getProto().returnType != factory.booleanArrayType
        && bootstrapMethod.getProto().returnType != factory.objectType) {
      return report("Unsupported dynamic constant (unsupported constant type)", context);
    }
    if (bootstrapMethod.getProto().getParameters().size() != 3) {
      return report("Unsupported dynamic constant (unsupported signature)", context);
    }
    if (bootstrapMethod.getProto().getParameters().get(0) != factory.lookupType) {
      return report(
          "Unsupported dynamic constant (unexpected type of first argument to bootstrap method",
          context);
    }
    if (bootstrapMethod.getProto().getParameters().get(1) != factory.stringType) {
      return report(
          "Unsupported dynamic constant (unexpected type of second argument to bootstrap method",
          context);
    }
    if (bootstrapMethod.getProto().getParameters().get(2) != factory.classType) {
      return report(
          "Unsupported dynamic constant (unexpected type of third argument to bootstrap method",
          context);
    }
    return DesugarDescription.builder()
        .setDesugarRewrite(
            (freshLocalProvider,
                localStackAllocator,
                eventConsumer,
                context1,
                methodProcessingContext,
                desugaringCollection,
                dexItemFactory) ->
                desugarConstDynamicInstruction(
                    instruction.asConstDynamic(),
                    freshLocalProvider,
                    localStackAllocator,
                    eventConsumer,
                    context1,
                    methodProcessingContext))
        .build();
  }

  @Override
  public void scan(ProgramMethod method, CfInstructionDesugaringEventConsumer eventConsumer) {
    for (CfInstruction instruction :
        method.getDefinition().getCode().asCfCode().getInstructions()) {
      compute(instruction, method).scan();
    }
  }

  private Collection<CfInstruction> desugarConstDynamicInstruction(
      CfConstDynamic invoke,
      FreshLocalProvider freshLocalProvider,
      LocalStackAllocator localStackAllocator,
      ConstantDynamicDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext) {
    ConstantDynamicClass constantDynamicClass =
        ensureConstantDynamicClass(invoke, context, methodProcessingContext, eventConsumer);
    return constantDynamicClass.desugarConstDynamicInstruction(
        invoke,
        freshLocalProvider,
        localStackAllocator,
        eventConsumer,
        context,
        methodProcessingContext);
  }

  // Creates a class corresponding to the constant dynamic symbolic reference and context.
  // TODO(b/178172809): Move this ensure handling to the synthetic items handling and move to
  //  one class for dynamic constants for each class using dynamic constants.
  private ConstantDynamicClass ensureConstantDynamicClass(
      CfConstDynamic constantDynamic,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext,
      ConstantDynamicDesugaringEventConsumer eventConsumer) {
    Map<ConstantDynamicReference, ConstantDynamicClass> dynamicConstantSyntheticsForClass =
        dynamicConstantSyntheticsPerClass.computeIfAbsent(
            context.getHolderType(), (ignore) -> new HashMap<>());
    ConstantDynamicClass result =
        dynamicConstantSyntheticsForClass.get(constantDynamic.getReference());
    if (result == null) {
      synchronized (dynamicConstantSyntheticsForClass) {
        result = dynamicConstantSyntheticsForClass.get(constantDynamic.getReference());
        if (result == null) {
          result =
              createConstantDynamicClass(
                  constantDynamic, context, methodProcessingContext, eventConsumer);
          dynamicConstantSyntheticsForClass.put(constantDynamic.getReference(), result);
        }
      }
    }
    return result;
  }

  // TODO(b/178172809): Change to use ensureFixedClass.
  private ConstantDynamicClass createConstantDynamicClass(
      CfConstDynamic constantDynamic,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext,
      ConstantDynamicDesugaringEventConsumer eventConsumer) {
    Box<ConstantDynamicClass> box = new Box<>();
    DexProgramClass clazz =
        appView
            .getSyntheticItems()
            .createClass(
                kinds -> kinds.CONST_DYNAMIC,
                methodProcessingContext.createUniqueContext(),
                appView,
                builder ->
                    box.set(new ConstantDynamicClass(builder, appView, context, constantDynamic)));
    // Immediately set the actual program class on the constant dynamic.
    ConstantDynamicClass constantDynamicClass = box.get();
    constantDynamicClass.setClass(clazz);
    eventConsumer.acceptConstantDynamicClass(constantDynamicClass, context);
    return constantDynamicClass;
  }
}
