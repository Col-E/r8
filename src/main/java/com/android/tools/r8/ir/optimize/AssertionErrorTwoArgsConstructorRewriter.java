// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.PostMethodProcessor;
import com.android.tools.r8.ir.desugar.backports.BackportedMethods;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.utils.InternalOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class AssertionErrorTwoArgsConstructorRewriter {

  private final AppView<?> appView;
  private final DexItemFactory dexItemFactory;
  private final InternalOptions options;

  public AssertionErrorTwoArgsConstructorRewriter(AppView<?> appView) {
    this.appView = appView;
    this.options = appView.options();
    this.dexItemFactory = appView.dexItemFactory();
  }

  @SuppressWarnings("ReferenceEquality")
  public void rewrite(
      IRCode code,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    assert !methodProcessor.isPostMethodProcessor();
    if (options.canUseAssertionErrorTwoArgumentConstructor()) {
      return;
    }
    AffectedValues affectedValues = new AffectedValues();
    ListIterator<BasicBlock> blockIterator = code.listIterator();
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      InstructionListIterator insnIterator = block.listIterator(code);
      List<NewInstance> newInstancesToRemove = new ArrayList<>();
      while (insnIterator.hasNext()) {
        InvokeDirect invoke = insnIterator.next().asInvokeDirect();
        if (invoke != null) {
          DexMethod invokedMethod = invoke.getInvokedMethod();
          if (invokedMethod == dexItemFactory.assertionErrorMethods.initMessageAndCause) {
            assert invoke.arguments().size() == 3; // receiver, message, cause
            Value newInstanceValue = invoke.getReceiver();
            Instruction definition = newInstanceValue.getDefinition();
            if (!definition.isNewInstance()) {
              continue;
            }
            InvokeStatic replacement =
                InvokeStatic.builder()
                    .setMethod(
                        createSynthetic(methodProcessor, methodProcessingContext).getReference())
                    .setFreshOutValue(
                        code, dexItemFactory.assertionErrorType.toTypeElement(appView))
                    .setPosition(invoke)
                    .setArguments(invoke.arguments().subList(1, 3))
                    .build();
            insnIterator.replaceCurrentInstruction(replacement);
            newInstanceValue.replaceUsers(replacement.outValue(), affectedValues);
            newInstancesToRemove.add(definition.asNewInstance());
          }
        }
      }
      newInstancesToRemove.forEach(
          newInstance -> newInstance.removeOrReplaceByDebugLocalRead(code));
    }
    affectedValues.widening(appView, code);
    assert code.isConsistentSSA(appView);
  }

  private final List<ProgramMethod> synthesizedMethods = new ArrayList<>();

  public List<ProgramMethod> getSynthesizedMethods() {
    return synthesizedMethods;
  }

  private ProgramMethod createSynthetic(
      MethodProcessor methodProcessor, MethodProcessingContext methodProcessingContext) {
    DexItemFactory factory = appView.dexItemFactory();
    DexProto proto =
        factory.createProto(factory.assertionErrorType, factory.stringType, factory.throwableType);
    ProgramMethod method =
        appView
            .getSyntheticItems()
            .createMethod(
                kinds -> kinds.BACKPORT,
                methodProcessingContext.createUniqueContext(),
                appView,
                builder ->
                    builder
                        .setApiLevelForCode(appView.computedMinApiLevel())
                        .setProto(proto)
                        .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                        .setCode(
                            methodSig ->
                                BackportedMethods.AssertionErrorMethods_createAssertionError(
                                    factory, methodSig)));
    synchronized (synthesizedMethods) {
      synthesizedMethods.add(method);
      OptimizationFeedback.getSimpleFeedback()
          .setDynamicReturnType(
              method,
              appView,
              DynamicType.createExact(
                  dexItemFactory
                      .assertionErrorType
                      .toTypeElement(appView, Nullability.definitelyNotNull())
                      .asClassType()));
    }
    methodProcessor
        .getEventConsumer()
        .acceptAssertionErrorCreateMethod(method, methodProcessingContext.getMethodContext());
    return method;
  }

  public void onLastWaveDone(PostMethodProcessor.Builder postMethodProcessorBuilder) {
    postMethodProcessorBuilder.addAll(synthesizedMethods, appView.graphLens());
  }
}
