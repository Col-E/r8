// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.icce;

import com.android.tools.r8.cf.code.CfConstNull;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult.FailedResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringCollection;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.DesugarDescription;
import com.android.tools.r8.ir.desugar.FreshLocalProvider;
import com.android.tools.r8.ir.desugar.LocalStackAllocator;
import com.android.tools.r8.ir.optimize.UtilityMethodsForCodeOptimizations;
import com.android.tools.r8.ir.optimize.UtilityMethodsForCodeOptimizations.MethodSynthesizerConsumer;
import com.android.tools.r8.ir.optimize.UtilityMethodsForCodeOptimizations.UtilityMethodForCodeOptimizations;
import java.util.ArrayList;
import java.util.Collection;

public class AlwaysThrowingInstructionDesugaring implements CfInstructionDesugaring {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;

  public AlwaysThrowingInstructionDesugaring(AppView<? extends AppInfoWithClassHierarchy> appView) {
    this.appView = appView;
  }

  @Override
  public Collection<CfInstruction> desugarInstruction(
      CfInstruction instruction,
      FreshLocalProvider freshLocalProvider,
      LocalStackAllocator localStackAllocator,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext,
      CfInstructionDesugaringCollection desugaringCollection,
      DexItemFactory dexItemFactory) {
    return computeDesugarDescription(instruction)
        .desugarInstruction(
            freshLocalProvider,
            localStackAllocator,
            eventConsumer,
            context,
            methodProcessingContext,
            dexItemFactory);
  }

  @Override
  public boolean needsDesugaring(CfInstruction instruction, ProgramMethod context) {
    return computeDesugarDescription(instruction).needsDesugaring();
  }

  private DesugarDescription computeDesugarDescription(CfInstruction instruction) {
    if (instruction.isInvoke()) {
      CfInvoke invoke = instruction.asInvoke();
      DexMethod invokedMethod = invoke.getMethod();
      MethodResolutionResult resolutionResult =
          appView.appInfo().resolveMethodLegacy(invokedMethod, invoke.isInterface());
      if (shouldRewriteInvokeToThrow(invoke, resolutionResult)) {
        return computeInvokeAsThrowRewrite(appView, invoke, resolutionResult);
      }
    }
    return DesugarDescription.nothing();
  }

  private boolean shouldRewriteInvokeToThrow(
      CfInvoke invoke, MethodResolutionResult resolutionResult) {
    if (resolutionResult.isArrayCloneMethodResult()) {
      return false;
    }
    if (resolutionResult.isFailedResolution()) {
      // For now don't materialize NSMEs from failed resolutions.
      return resolutionResult.asFailedResolution().hasMethodsCausingError();
    }
    assert resolutionResult.isSingleResolution();
    return resolutionResult.getResolvedMethod().isStatic() != invoke.isInvokeStatic();
  }

  public static DesugarDescription computeInvokeAsThrowRewrite(
      AppView<?> appView, CfInvoke invoke, MethodResolutionResult resolutionResult) {
    return DesugarDescription.builder()
        .setDesugarRewrite(
            (freshLocalProvider,
                localStackAllocator,
                eventConsumer,
                context,
                methodProcessingContext,
                dexItemFactory) ->
                getThrowInstructions(
                    appView,
                    invoke,
                    resolutionResult,
                    localStackAllocator,
                    eventConsumer,
                    context,
                    methodProcessingContext))
        .build();
  }

  private static Collection<CfInstruction> getThrowInstructions(
      AppView<?> appView,
      CfInvoke invoke,
      MethodResolutionResult resolutionResult,
      LocalStackAllocator localStackAllocator,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext) {
    MethodSynthesizerConsumer methodSynthesizerConsumer = null;
    if (resolutionResult == null) {
      methodSynthesizerConsumer =
          UtilityMethodsForCodeOptimizations::synthesizeThrowNoSuchMethodErrorMethod;
    } else if (resolutionResult.isSingleResolution()) {
      if (resolutionResult.getResolvedMethod().isStatic() != invoke.isInvokeStatic()) {
        methodSynthesizerConsumer =
            UtilityMethodsForCodeOptimizations::synthesizeThrowIncompatibleClassChangeErrorMethod;
      }
    } else if (resolutionResult.isFailedResolution()) {
      FailedResolutionResult failedResolutionResult = resolutionResult.asFailedResolution();
      AppInfoWithClassHierarchy appInfo = appView.appInfoForDesugaring();
      if (failedResolutionResult.isIllegalAccessErrorResult(context.getHolder(), appInfo)) {
        methodSynthesizerConsumer =
            UtilityMethodsForCodeOptimizations::synthesizeThrowIllegalAccessErrorMethod;
      } else if (failedResolutionResult.isNoSuchMethodErrorResult(context.getHolder(), appInfo)) {
        methodSynthesizerConsumer =
            UtilityMethodsForCodeOptimizations::synthesizeThrowNoSuchMethodErrorMethod;
      } else if (failedResolutionResult.isIncompatibleClassChangeErrorResult()) {
        methodSynthesizerConsumer =
            UtilityMethodsForCodeOptimizations::synthesizeThrowIncompatibleClassChangeErrorMethod;
      }
    }

    if (methodSynthesizerConsumer == null) {
      assert false;
      return null;
    }

    // Replace the entire effect of the invoke by by call to the throwing helper:
    //   ...
    //   invoke <method> [receiver] args*
    // =>
    //   ...
    //   (pop arg)*
    //   [pop receiver]
    //   invoke <throwing-method>
    //   pop exception result
    //   [push fake result for <method>]
    UtilityMethodForCodeOptimizations throwMethod =
        methodSynthesizerConsumer.synthesizeMethod(appView, methodProcessingContext);
    ProgramMethod throwProgramMethod = throwMethod.uncheckedGetMethod();
    eventConsumer.acceptThrowMethod(throwProgramMethod, context);

    ArrayList<CfInstruction> replacement = new ArrayList<>();
    DexTypeList parameters = invoke.getMethod().getParameters();
    for (int i = parameters.values.length - 1; i >= 0; i--) {
      replacement.add(
          new CfStackInstruction(
              parameters.get(i).isWideType()
                  ? CfStackInstruction.Opcode.Pop2
                  : CfStackInstruction.Opcode.Pop));
    }
    if (!invoke.isInvokeStatic()) {
      replacement.add(new CfStackInstruction(CfStackInstruction.Opcode.Pop));
    }

    CfInvoke throwInvoke =
        new CfInvoke(
            org.objectweb.asm.Opcodes.INVOKESTATIC, throwProgramMethod.getReference(), false);
    assert throwInvoke.getMethod().getReturnType().isClassType();
    replacement.add(throwInvoke);
    replacement.add(new CfStackInstruction(CfStackInstruction.Opcode.Pop));

    DexType returnType = invoke.getMethod().getReturnType();
    if (!returnType.isVoidType()) {
      replacement.add(
          returnType.isPrimitiveType()
              ? new CfConstNumber(0, ValueType.fromDexType(returnType))
              : new CfConstNull());
    } else {
      // If the return type is void, the stack may need an extra slot to fit the return type of
      // the call to the throwing method.
      localStackAllocator.allocateLocalStack(1);
    }
    return replacement;
  }
}
