// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.lambda;

import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfInvokeDynamic;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.FreshLocalProvider;
import com.android.tools.r8.ir.desugar.LambdaClass;
import com.android.tools.r8.ir.desugar.LambdaDescriptor;
import com.android.tools.r8.synthesis.SyntheticNaming;
import com.android.tools.r8.utils.Box;
import com.google.common.collect.ImmutableList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import org.objectweb.asm.Opcodes;

public class LambdaInstructionDesugaring implements CfInstructionDesugaring {

  private final AppView<?> appView;

  public LambdaInstructionDesugaring(AppView<?> appView) {
    this.appView = appView;
  }

  @Override
  public Collection<CfInstruction> desugarInstruction(
      CfInstruction instruction,
      FreshLocalProvider freshLocalProvider,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext) {
    if (instruction.isInvokeDynamic()) {
      return desugarInvokeDynamicInstruction(
          instruction.asInvokeDynamic(),
          freshLocalProvider,
          eventConsumer,
          context,
          methodProcessingContext);
    }
    return null;
  }

  private Collection<CfInstruction> desugarInvokeDynamicInstruction(
      CfInvokeDynamic invoke,
      FreshLocalProvider freshLocalProvider,
      LambdaDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext) {
    LambdaClass lambdaClass = createLambdaClass(invoke, context, methodProcessingContext);
    if (lambdaClass == null) {
      return null;
    }

    eventConsumer.acceptLambdaClass(lambdaClass, context);

    if (lambdaClass.isStateless()) {
      return ImmutableList.of(
          new CfFieldInstruction(
              Opcodes.GETSTATIC, lambdaClass.lambdaField, lambdaClass.lambdaField));
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
    return replacement;
  }

  // Creates a lambda class corresponding to the lambda descriptor and context.
  private LambdaClass createLambdaClass(
      CfInvokeDynamic invoke,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext) {
    LambdaDescriptor descriptor =
        LambdaDescriptor.tryInfer(invoke.getCallSite(), appView.appInfoForDesugaring(), context);
    if (descriptor == null) {
      return null;
    }

    Box<LambdaClass> box = new Box<>();
    DexProgramClass clazz =
        appView
            .getSyntheticItems()
            .createClass(
                SyntheticNaming.SyntheticKind.LAMBDA,
                methodProcessingContext.createUniqueContext(),
                appView.dexItemFactory(),
                builder -> box.set(new LambdaClass(builder, appView, this, context, descriptor)));
    // Immediately set the actual program class on the lambda.
    LambdaClass lambdaClass = box.get();
    lambdaClass.setClass(clazz);
    return lambdaClass;
  }

  @Override
  public boolean needsDesugaring(CfInstruction instruction, ProgramMethod context) {
    return instruction.isInvokeDynamic()
        && LambdaDescriptor.tryInfer(
                instruction.asInvokeDynamic().getCallSite(),
                appView.appInfoForDesugaring(),
                context)
            != null;
  }
}
