// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.api;

import static com.android.tools.r8.utils.AndroidApiLevelUtils.isOutlinedAtSameOrLowerLevel;

import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.PostMethodProcessor;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.synthetic.ForwardMethodBuilder;
import com.android.tools.r8.ir.synthetic.NewInstanceSourceCode;
import com.android.tools.r8.shaking.ComputeApiLevelUseRegistry;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The InstanceInitializerOutliner will outline instance initializers and their NewInstance source.
 * Unlike the ApiInvokeOutlinerDesugaring that works on CF, this works on IR to properly replace the
 * users of the NewInstance call.
 */
public class InstanceInitializerOutliner {

  private final AppView<?> appView;
  private final DexItemFactory factory;

  private final List<ProgramMethod> synthesizedMethods = new ArrayList<>();

  public InstanceInitializerOutliner(AppView<?> appView) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
  }

  public List<ProgramMethod> getSynthesizedMethods() {
    return synthesizedMethods;
  }

  public void rewriteInstanceInitializers(
      IRCode code,
      ProgramMethod context,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    assert !methodProcessor.isPostMethodProcessor();
    // Do not outline from already synthesized methods.
    if (context.getDefinition().isD8R8Synthesized()) {
      return;
    }
    Map<NewInstance, Value> rewrittenNewInstances = new IdentityHashMap<>();
    ComputedApiLevel minApiLevel = appView.computedMinApiLevel();
    InstructionListIterator iterator = code.instructionListIterator();
    // Scan over the code to find <init> calls that needs to be outlined.
    while (iterator.hasNext()) {
      Instruction instruction = iterator.next();
      InvokeDirect invokeDirect = instruction.asInvokeDirect();
      if (invokeDirect == null) {
        continue;
      }
      DexMethod invokedConstructor = invokeDirect.getInvokedMethod();
      if (!invokedConstructor.isInstanceInitializer(factory)) {
        continue;
      }
      Value firstOperand = invokeDirect.getFirstOperand();
      if (firstOperand.isPhi()) {
        continue;
      }
      NewInstance newInstance = firstOperand.getDefinition().asNewInstance();
      if (newInstance == null) {
        // We could not find a new instance call associated with the init, this is probably a
        // constructor call to the super class.
        continue;
      }
      ComputedApiLevel apiReferenceLevel =
          appView
              .apiLevelCompute()
              .computeApiLevelForLibraryReference(invokedConstructor, minApiLevel);
      if (minApiLevel.isGreaterThanOrEqualTo(apiReferenceLevel)) {
        continue;
      }
      // Check if this is already outlined.
      if (isOutlinedAtSameOrLowerLevel(context.getHolder(), apiReferenceLevel)) {
        continue;
      }
      DexEncodedMethod synthesizedInstanceInitializer =
          createSynthesizedInstanceInitializer(
              invokeDirect.getInvokedMethod(),
              apiReferenceLevel,
              methodProcessor,
              methodProcessingContext);
      List<Value> arguments = instruction.inValues();
      InvokeStatic outlinedMethodInvoke =
          InvokeStatic.builder()
              .setMethod(synthesizedInstanceInitializer.getReference())
              .setPosition(instruction)
              .setFreshOutValue(code, newInstance.getOutType())
              .setArguments(arguments.subList(1, arguments.size()))
              .build();
      iterator.replaceCurrentInstruction(outlinedMethodInvoke);
      rewrittenNewInstances.put(newInstance, outlinedMethodInvoke.outValue());
    }
    if (rewrittenNewInstances.isEmpty()) {
      return;
    }
    // Scan over NewInstance calls that needs to be outlined. We insert a call to a synthetic method
    // with a NewInstance to preserve class-init semantics.
    // TODO(b/244284945): If we know that arguments to an init cannot change class initializer
    //  semantics we can avoid inserting the NewInstance outline.
    iterator = code.instructionListIterator();
    Set<Value> newOutValues = Sets.newIdentityHashSet();
    while (iterator.hasNext()) {
      Instruction instruction = iterator.next();
      if (!instruction.isNewInstance()) {
        continue;
      }
      NewInstance newInstance = instruction.asNewInstance();
      Value newOutlineInstanceValue = rewrittenNewInstances.get(newInstance);
      if (newOutlineInstanceValue == null) {
        continue;
      }
      newInstance.outValue().replaceUsers(newOutlineInstanceValue);
      newOutValues.add(newOutlineInstanceValue);
      if (canSkipClInit(iterator, newInstance, newOutlineInstanceValue)) {
        iterator.removeOrReplaceByDebugLocalRead();
        continue;
      }
      ComputedApiLevel classApiLevel =
          appView
              .apiLevelCompute()
              .computeApiLevelForLibraryReference(newInstance.getType(), minApiLevel);
      assert classApiLevel.isKnownApiLevel();
      DexEncodedMethod synthesizedNewInstance =
          createSynthesizedNewInstance(
              newInstance.getType(), classApiLevel, methodProcessor, methodProcessingContext);
      InvokeStatic outlinedStaticInit =
          InvokeStatic.builder()
              .setMethod(synthesizedNewInstance.getReference())
              .setPosition(instruction)
              .build();
      iterator.replaceCurrentInstruction(outlinedStaticInit);
    }
    // We are changing a NewInstance to a method call where we loose that the type is not null.
    assert !newOutValues.isEmpty();
    new TypeAnalysis(appView, code).widening(newOutValues);

    // Outlining of instance initializers will in most cases change the api level of the context
    // since all other soft verification issues has been outlined. To ensure that we do not inline
    // the outline again in R8 - but allow inlining of other calls to min api level methods, we have
    // to recompute the api level.
    if (appView.enableWholeProgramOptimizations()) {
      recomputeApiLevel(context, code);
    }

    assert code.isConsistentSSA(appView);
  }

  public void onLastWaveDone(PostMethodProcessor.Builder postMethodProcessorBuilder) {
    postMethodProcessorBuilder.addAll(synthesizedMethods, appView.graphLens());
  }

  private boolean canSkipClInit(
      InstructionListIterator iterator, NewInstance newInstance, Value newInstanceOutValue) {
    InvokeStatic definition = newInstanceOutValue.getDefinition().asInvokeStatic();
    assert definition != null;
    Position currentPosition = newInstance.getPosition();
    // We can skip constant and debug local read instructions when searching for next instruction.
    Instruction nextInstruction =
        iterator.nextUntil(
            instruction -> {
              if (!instruction.isConstInstruction() && !instruction.isDebugLocalRead()) {
                return true;
              }
              return isChangeInPosition(currentPosition, instruction.getPosition());
            });
    iterator.previousUntil(instruction -> instruction == newInstance);
    Instruction newInstanceCopy = iterator.next();
    assert newInstanceCopy == newInstance;
    // Check if there is an instruction between the new-instance and init call that is not constant.
    return nextInstruction == definition
        && !isChangeInPosition(currentPosition, nextInstruction.getPosition());
  }

  private boolean isChangeInPosition(Position currentPosition, Position nextPosition) {
    return !nextPosition.isNone() && currentPosition.getLine() != nextPosition.getLine();
  }

  private void recomputeApiLevel(ProgramMethod context, IRCode code) {
    DexEncodedMethod definition = context.getDefinition();
    if (!definition.getApiLevelForCode().isKnownApiLevel()) {
      // This is either D8 or the api level is unknown.
      return;
    }
    ComputeApiLevelUseRegistry registry =
        new ComputeApiLevelUseRegistry(appView, context, appView.apiLevelCompute());
    code.registerCodeReferences(context, registry);
    ComputedApiLevel maxApiReferenceLevel = registry.getMaxApiReferenceLevel();
    assert maxApiReferenceLevel.isKnownApiLevel();
    definition.setApiLevelForCode(maxApiReferenceLevel);
  }

  private DexEncodedMethod createSynthesizedNewInstance(
      DexType targetType,
      ComputedApiLevel computedApiLevel,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    DexProto proto = appView.dexItemFactory().createProto(factory.voidType);
    ProgramMethod method =
        appView
            .getSyntheticItems()
            .createMethod(
                kinds -> kinds.API_MODEL_OUTLINE,
                methodProcessingContext.createUniqueContext(),
                appView,
                builder ->
                    builder
                        .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                        .setProto(proto)
                        .setApiLevelForDefinition(appView.computedMinApiLevel())
                        .setApiLevelForCode(computedApiLevel)
                        .setCode(
                            m ->
                                NewInstanceSourceCode.create(appView, m.getHolderType(), targetType)
                                    .generateCfCode()));
    methodProcessor
        .getEventConsumer()
        .acceptInstanceInitializerOutline(method, methodProcessingContext.getMethodContext());
    synchronized (synthesizedMethods) {
      synthesizedMethods.add(method);
    }
    return method.getDefinition();
  }

  private DexEncodedMethod createSynthesizedInstanceInitializer(
      DexMethod targetMethod,
      ComputedApiLevel computedApiLevel,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    DexProto proto =
        appView
            .dexItemFactory()
            .createProto(targetMethod.getHolderType(), targetMethod.getParameters());
    ProgramMethod method =
        appView
            .getSyntheticItems()
            .createMethod(
                kinds -> kinds.API_MODEL_OUTLINE,
                methodProcessingContext.createUniqueContext(),
                appView,
                builder ->
                    builder
                        .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                        .setProto(proto)
                        .setApiLevelForDefinition(appView.computedMinApiLevel())
                        .setApiLevelForCode(computedApiLevel)
                        .setCode(
                            m ->
                                ForwardMethodBuilder.builder(appView.dexItemFactory())
                                    .setConstructorTargetWithNewInstance(targetMethod)
                                    .setStaticSource(m)
                                    .build()));
    methodProcessor
        .getEventConsumer()
        .acceptInstanceInitializerOutline(method, methodProcessingContext.getMethodContext());
    synchronized (synthesizedMethods) {
      synthesizedMethods.add(method);
      ClassTypeElement exactType =
          targetMethod
              .getHolderType()
              .toTypeElement(appView, Nullability.definitelyNotNull())
              .asClassType();
      OptimizationFeedback.getSimpleFeedback()
          .setDynamicReturnType(method, appView, DynamicType.createExact(exactType));
    }
    return method.getDefinition();
  }
}
