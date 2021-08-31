// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.constantdynamic;

import com.android.tools.r8.cf.code.CfConstDynamic;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.FreshLocalProvider;
import com.android.tools.r8.ir.desugar.LocalStackAllocator;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.Box;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.Opcodes;

public class ConstantDynamicInstructionDesugaring implements CfInstructionDesugaring {

  private final AppView<?> appView;
  private final Map<DexType, Map<ConstantDynamicReference, ConstantDynamicClass>>
      dynamicConstantSyntheticsPerClass = new ConcurrentHashMap<>();

  public ConstantDynamicInstructionDesugaring(AppView<?> appView) {
    this.appView = appView;
  }

  @Override
  public boolean needsDesugaring(CfInstruction instruction, ProgramMethod context) {
    return instruction.isConstDynamic();
  }

  @Override
  public Collection<CfInstruction> desugarInstruction(
      CfInstruction instruction,
      FreshLocalProvider freshLocalProvider,
      LocalStackAllocator localStackAllocator,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext,
      DexItemFactory dexItemFactory) {
    if (instruction.isConstDynamic()) {
      return desugarConstDynamicInstruction(
          instruction.asConstDynamic(),
          freshLocalProvider,
          localStackAllocator,
          eventConsumer,
          context,
          methodProcessingContext);
    }
    return null;
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
    return ImmutableList.of(
        new CfInvoke(Opcodes.INVOKESTATIC, constantDynamicClass.getConstMethod, false));
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
                SyntheticKind.CONST_DYNAMIC,
                methodProcessingContext.createUniqueContext(),
                appView,
                builder ->
                    box.set(
                        new ConstantDynamicClass(
                            builder, appView, this, context, constantDynamic)));
    // Immediately set the actual program class on the constant dynamic.
    ConstantDynamicClass constantDynamicClass = box.get();
    constantDynamicClass.setClass(clazz);
    eventConsumer.acceptConstantDynamicClass(constantDynamicClass, context);
    return constantDynamicClass;
  }
}
