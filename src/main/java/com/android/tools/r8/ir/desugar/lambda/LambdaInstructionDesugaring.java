// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.lambda;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfInvokeDynamic;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.cf.code.CfStaticFieldRead;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.desugar.CfDesugaringInfo;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.DesugarDescription;
import com.android.tools.r8.ir.desugar.FreshLocalProvider;
import com.android.tools.r8.ir.desugar.LambdaClass;
import com.android.tools.r8.ir.desugar.LambdaDescriptor;
import com.android.tools.r8.ir.desugar.LocalStackAllocator;
import com.android.tools.r8.utils.Box;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Set;
import org.objectweb.asm.Opcodes;

public class LambdaInstructionDesugaring implements CfInstructionDesugaring {

  private final AppView<?> appView;
  private final Set<DexMethod> directTargetedLambdaImplementationMethods =
      Sets.newIdentityHashSet();

  public boolean isDirectTargetedLambdaImplementationMethod(DexMethodHandle implMethod) {
    return implMethod.type.isInvokeDirect()
        && directTargetedLambdaImplementationMethods.contains(implMethod.asMethod());
  }

  public LambdaInstructionDesugaring(AppView<?> appView) {
    this.appView = appView;
  }

  @Override
  public void scan(ProgramMethod method, CfInstructionDesugaringEventConsumer eventConsumer) {
    CfCode code = method.getDefinition().getCode().asCfCode();
    for (CfInstruction instruction : code.getInstructions()) {
      if (instruction.isInvokeSpecial()) {
        DexMethod target = instruction.asInvoke().getMethod();
        if (target.getName().startsWith(appView.dexItemFactory().javacLambdaMethodPrefix)) {
          directTargetedLambdaImplementationMethods.add(target);
        }
      }
    }
  }

  @Override
  public DesugarDescription compute(CfInstruction instruction, ProgramMethod context) {
    if (!isLambdaInvoke(instruction, context, appView)) {
      return DesugarDescription.nothing();
    }
    return desugarInstruction(instruction);
  }

  private DesugarDescription desugarInstruction(CfInstruction instruction) {
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
                desugarInvokeDynamicInstruction(
                    instruction.asInvokeDynamic(),
                    freshLocalProvider,
                    localStackAllocator,
                    desugaringInfo,
                    eventConsumer,
                    context,
                    methodProcessingContext,
                    (invoke, localProvider, stackAllocator) ->
                        desugaringCollection.desugarInstruction(
                            invoke,
                            localProvider,
                            stackAllocator,
                            desugaringInfo,
                            eventConsumer,
                            context,
                            methodProcessingContext)))
        .build();
  }

  public interface DesugarInvoke {
    Collection<CfInstruction> desugarInvoke(
        CfInvoke invoke,
        FreshLocalProvider freshLocalProvider,
        LocalStackAllocator localStackAllocator);
  }

  private Collection<CfInstruction> desugarInvokeDynamicInstruction(
      CfInvokeDynamic invoke,
      FreshLocalProvider freshLocalProvider,
      LocalStackAllocator localStackAllocator,
      CfDesugaringInfo desugaringInfo,
      LambdaDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext,
      DesugarInvoke desugarInvoke) {
    LambdaClass lambdaClass =
        createLambdaClass(
            invoke,
            context,
            methodProcessingContext,
            desugarInvoke,
            !desugaringInfo.canIncreaseBytecodeSize());
    if (lambdaClass == null) {
      return null;
    }

    eventConsumer.acceptLambdaClass(lambdaClass, context);

    if (lambdaClass.hasFactoryMethod()) {
      return ImmutableList.of(
          new CfInvoke(Opcodes.INVOKESTATIC, lambdaClass.getFactoryMethod(), false));
    }

    if (lambdaClass.isStatelessSingleton()) {
      return ImmutableList.of(
          new CfStaticFieldRead(lambdaClass.lambdaField, lambdaClass.lambdaField));
    }

    DexTypeList captureTypes = lambdaClass.descriptor.captures;
    Deque<CfInstruction> replacement = new ArrayDeque<>(3 + captureTypes.size() * 2);
    replacement.add(new CfNew(lambdaClass.getType()));
    replacement.add(new CfStackInstruction(Opcode.Dup));
    captureTypes.forEach(
        captureType -> {
          ValueType valueType = ValueType.fromDexType(captureType);
          int freshLocal = freshLocalProvider.getFreshLocal(valueType.requiredRegisters());
          replacement.addFirst(new CfStore(valueType, freshLocal));
          replacement.addLast(new CfLoad(valueType, freshLocal));
        });
    replacement.add(new CfInvoke(Opcodes.INVOKESPECIAL, lambdaClass.constructor, false));

    // Coming into the original invoke-dynamic instruction, we have N arguments on the stack. We pop
    // the N arguments from the stack, and then add a new-instance and dup it. With those two new
    // elements on the stack, we load all the N arguments back onto the stack. At this point, we
    // have the original N arguments on the stack plus the 2 new stack elements.
    localStackAllocator.allocateLocalStack(2);
    return replacement;
  }

  // Creates a lambda class corresponding to the lambda descriptor and context.
  private LambdaClass createLambdaClass(
      CfInvokeDynamic invoke,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext,
      DesugarInvoke desugarInvoke,
      boolean useFactoryMethodForConstruction) {
    LambdaDescriptor descriptor =
        LambdaDescriptor.tryInfer(
            invoke.getCallSite(), appView, appView.appInfoForDesugaring(), context);
    if (descriptor == null) {
      return null;
    }

    Box<LambdaClass> box = new Box<>();
    DexProgramClass clazz =
        appView
            .getSyntheticItems()
            .createClass(
                kinds -> kinds.LAMBDA,
                methodProcessingContext.createUniqueContext(),
                appView,
                builder ->
                    box.set(
                        new LambdaClass(
                            builder,
                            appView,
                            this,
                            context,
                            descriptor,
                            desugarInvoke,
                            useFactoryMethodForConstruction)));
    // Immediately set the actual program class on the lambda.
    LambdaClass lambdaClass = box.get();
    lambdaClass.setClass(clazz);
    return lambdaClass;
  }

  public static boolean isLambdaInvoke(
      CfInstruction instruction, ProgramMethod context, AppView<?> appView) {
    return instruction.isInvokeDynamic()
        && LambdaDescriptor.tryInfer(
                instruction.asInvokeDynamic().getCallSite(),
                appView,
                appView.appInfoForDesugaring(),
                context)
            != null;
  }
}
