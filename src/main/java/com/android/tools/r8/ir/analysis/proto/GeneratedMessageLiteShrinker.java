// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto;

import static com.android.tools.r8.graph.DexClassAndMethod.asProgramMethodOrNull;
import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.ir.analysis.proto.ProtoUtils.getInfoValueFromMessageInfoConstructionInvoke;
import static com.android.tools.r8.ir.analysis.proto.ProtoUtils.getObjectsValueFromMessageInfoConstructionInvoke;
import static com.android.tools.r8.ir.analysis.proto.ProtoUtils.setObjectsValueForMessageInfoConstructionInvoke;
import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;

import com.android.tools.r8.graph.AccessControl;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMember;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.proto.schema.ProtoMessageInfo;
import com.android.tools.r8.ir.analysis.proto.schema.ProtoObject;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.ArrayPut;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.IRCodeUtils;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.InvokeNewArray;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.NewArrayEmpty;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.conversion.MethodProcessorEventConsumer;
import com.android.tools.r8.ir.conversion.OneTimeMethodProcessor;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackIgnore;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.DependentMinimumKeepInfoCollection;
import com.android.tools.r8.shaking.KeepMethodInfo;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class GeneratedMessageLiteShrinker {

  private final AppView<?> appView;
  private final RawMessageInfoDecoder decoder;
  private final RawMessageInfoEncoder encoder;
  private final ProtoReferences references;

  private final TypeElement objectArrayType;
  private final TypeElement stringType;

  public GeneratedMessageLiteShrinker(
      AppView<?> appView, RawMessageInfoDecoder decoder, ProtoReferences references) {
    this.appView = appView;
    this.decoder = decoder;
    this.encoder = new RawMessageInfoEncoder(appView.dexItemFactory());
    this.references = references;

    // Types.
    this.objectArrayType =
        TypeElement.fromDexType(
            appView.dexItemFactory().objectArrayType, definitelyNotNull(), appView);
    this.stringType = TypeElement.stringClassType(appView, definitelyNotNull());
  }

  public void extendRootSet(DependentMinimumKeepInfoCollection dependentMinimumKeepInfo) {
    // Disable optimizations for various methods that are modeled, to ensure that we can still
    // recognize the uses of these methods even after optimizations have been run.
    DexProgramClass generatedMessageLiteClass =
        asProgramClassOrNull(
            appView
                .appInfo()
                .definitionForWithoutExistenceAssert(references.generatedMessageLiteType));
    if (generatedMessageLiteClass != null) {
      ProgramMethod dynamicMethod =
          generatedMessageLiteClass.lookupProgramMethod(references.dynamicMethod);
      if (dynamicMethod != null) {
        disallowSignatureOptimizations(
            dependentMinimumKeepInfo
                .getOrCreateUnconditionalMinimumKeepInfoFor(dynamicMethod.getReference())
                .asMethodJoiner());
      }

      references.forEachMethodReference(
          reference -> {
            DexProgramClass holder =
                asProgramClassOrNull(appView.definitionFor(reference.getHolderType()));
            ProgramMethod method = reference.lookupOnProgramClass(holder);
            if (method != null) {
              disallowSignatureOptimizations(
                  dependentMinimumKeepInfo
                      .getOrCreateUnconditionalMinimumKeepInfoFor(method.getReference())
                      .asMethodJoiner());
            }
          });
    }

    DexProgramClass rawMessageInfoClass =
        asProgramClassOrNull(
            appView.appInfo().definitionForWithoutExistenceAssert(references.rawMessageInfoType));
    if (rawMessageInfoClass != null) {
      disallowOptimization(
          rawMessageInfoClass, references.rawMessageInfoInfoField, dependentMinimumKeepInfo);
      disallowOptimization(
          rawMessageInfoClass, references.rawMessageInfoObjectsField, dependentMinimumKeepInfo);
    }
  }

  private void disallowSignatureOptimizations(KeepMethodInfo.Joiner methodJoiner) {
    methodJoiner
        .disallowConstantArgumentOptimization()
        .disallowMethodStaticizing()
        .disallowParameterRemoval()
        .disallowParameterReordering()
        .disallowParameterTypeStrengthening()
        .disallowReturnTypeStrengthening()
        .disallowUnusedArgumentOptimization()
        .disallowUnusedReturnValueOptimization();
  }

  private void disallowOptimization(
      DexProgramClass clazz,
      DexMember<?, ?> reference,
      DependentMinimumKeepInfoCollection dependentMinimumKeepInfo) {
    ProgramMember<?, ?> member = clazz.lookupProgramMember(reference);
    if (member != null) {
      dependentMinimumKeepInfo
          .getOrCreateUnconditionalMinimumKeepInfoFor(reference)
          .disallowOptimization();
    }
  }

  public void run(IRCode code) {
    ProgramMethod method = code.context();
    if (references.isDynamicMethod(method.getReference())) {
      rewriteDynamicMethod(method, code);
    } else if (appView.hasLiveness()) {
      optimizeNewMutableInstance(appView.withLiveness(), code);
    }
  }

  private void optimizeNewMutableInstance(AppView<AppInfoWithLiveness> appView, IRCode code) {
    AffectedValues affectedValues = new AffectedValues();
    BasicBlockIterator blockIterator = code.listIterator();
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      InstructionListIterator instructionIterator = block.listIterator(code);
      while (instructionIterator.hasNext()) {
        Instruction instruction = instructionIterator.next();
        DexType newMutableInstanceType = getNewMutableInstanceType(appView, instruction);
        if (newMutableInstanceType == null) {
          continue;
        }

        DexMethod instanceInitializerReference =
            appView.dexItemFactory().createInstanceInitializer(newMutableInstanceType);
        ProgramMethod instanceInitializer =
            asProgramMethodOrNull(appView.definitionFor(instanceInitializerReference));
        if (instanceInitializer == null
            || AccessControl.isMemberAccessible(
                    instanceInitializer, instanceInitializer.getHolder(), code.context(), appView)
                .isPossiblyFalse()) {
          continue;
        }

        NewInstance newInstance =
            NewInstance.builder()
                .setType(newMutableInstanceType)
                .setFreshOutValue(
                    code, newMutableInstanceType.toTypeElement(appView, definitelyNotNull()))
                .setPosition(instruction)
                .build();
        instructionIterator.replaceCurrentInstruction(newInstance, affectedValues);

        InvokeDirect constructorInvoke =
            InvokeDirect.builder()
                .setMethod(instanceInitializerReference)
                .setSingleArgument(newInstance.outValue())
                .setPosition(instruction)
                .build();

        if (block.hasCatchHandlers()) {
          // Split the block after the new-instance instruction and insert the constructor call in
          // the split block.
          BasicBlock splitBlock =
              instructionIterator.splitCopyCatchHandlers(code, blockIterator, appView.options());
          instructionIterator = splitBlock.listIterator(code);
          instructionIterator.add(constructorInvoke);
          BasicBlock previousBlock =
              blockIterator.previousUntil(previous -> previous == splitBlock);
          assert previousBlock != null;
          blockIterator.next();
        } else {
          instructionIterator.add(constructorInvoke);
        }
      }
    }
    affectedValues.narrowingWithAssumeRemoval(appView, code);
    assert code.isConsistentSSA(appView);
  }

  private DexType getNewMutableInstanceType(
      AppView<AppInfoWithLiveness> appView, Instruction instruction) {
    if (!instruction.isInvokeMethodWithReceiver()) {
      return null;
    }
    InvokeMethodWithReceiver invoke = instruction.asInvokeMethodWithReceiver();
    DexMethod invokedMethod = invoke.getInvokedMethod();
    if (!references.isDynamicMethod(invokedMethod)
        && !references.isDynamicMethodBridge(invokedMethod)) {
      return null;
    }
    assert invokedMethod.getParameter(0) == references.methodToInvokeType;
    if (!references.methodToInvokeMembers.isNewMutableInstanceEnum(
        invoke.getFirstNonReceiverArgument())) {
      return null;
    }
    TypeElement receiverType = invoke.getReceiver().getDynamicUpperBoundType(appView);
    if (!receiverType.isClassType()) {
      return null;
    }
    DexType rawReceiverType = receiverType.asClassType().getClassType();
    return appView.appInfo().isStrictSubtypeOf(rawReceiverType, references.generatedMessageLiteType)
        ? rawReceiverType
        : null;
  }

  public void postOptimizeDynamicMethods(
      IRConverter converter, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    timing.begin("[Proto] Post optimize dynamic methods");
    ProgramMethodSet wave = ProgramMethodSet.create(this::forEachDynamicMethod);
    MethodProcessorEventConsumer eventConsumer = MethodProcessorEventConsumer.empty();
    OneTimeMethodProcessor methodProcessor =
        OneTimeMethodProcessor.create(wave, eventConsumer, appView);
    methodProcessor.forEachWaveWithExtension(
        (method, methodProcessingContext) ->
            converter.processDesugaredMethod(
                method,
                OptimizationFeedbackIgnore.getInstance(),
                methodProcessor,
                methodProcessingContext,
                MethodConversionOptions.forLirPhase(appView)),
        executorService);
    timing.end();
  }

  private void forEachDynamicMethod(Consumer<ProgramMethod> consumer) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    appView
        .appInfoWithLiveness()
        .forEachInstantiatedSubType(
            references.generatedMessageLiteType,
            clazz -> {
              DexMethod dynamicMethodReference =
                  dexItemFactory.createMethod(
                      clazz.type, references.dynamicMethodProto, references.dynamicMethodName);
              ProgramMethod dynamicMethod = clazz.lookupProgramMethod(dynamicMethodReference);
              if (dynamicMethod != null) {
                consumer.accept(dynamicMethod);
              }
            },
            lambda -> {
              assert false;
            });
  }

  /**
   * Finds all const-string instructions in the code that flows into GeneratedMessageLite.
   * newMessageInfo(), and rewrites them into a dex-item-based-const-string if the string value
   * corresponds to the name of an instance field of the enclosing class.
   *
   * <p>NOTE: This is work in progress. Understanding the full semantics of the arguments passed to
   * newMessageInfo is still pending.
   */
  private void rewriteDynamicMethod(ProgramMethod method, IRCode code) {
    InvokeMethod newMessageInfoInvoke = getNewMessageInfoInvoke(code, references);
    if (newMessageInfoInvoke != null) {
      Value infoValue =
          getInfoValueFromMessageInfoConstructionInvoke(newMessageInfoInvoke, references);
      Value objectsValue =
          getObjectsValueFromMessageInfoConstructionInvoke(newMessageInfoInvoke, references);

      // Decode the arguments passed to newMessageInfo().
      ProtoMessageInfo protoMessageInfo = decoder.run(method, infoValue, objectsValue);
      if (protoMessageInfo != null) {
        // Rewrite the arguments to newMessageInfo().
        rewriteArgumentsToNewMessageInfo(code, newMessageInfoInvoke, infoValue, protoMessageInfo);

        // Ensure that the definition of the original `objects` value is removed.
        IRCodeUtils.removeArrayAndTransitiveInputsIfNotUsed(code, objectsValue.definition);
      } else {
        // We should generally be able to decode the arguments passed to newMessageInfo().
        assert false;
      }
    }
    assert code.isConsistentSSA(appView);
  }

  private void rewriteArgumentsToNewMessageInfo(
      IRCode code,
      InvokeMethod newMessageInfoInvoke,
      Value infoValue,
      ProtoMessageInfo protoMessageInfo) {
    rewriteInfoArgumentToNewMessageInfo(code, infoValue, protoMessageInfo);
    rewriteObjectsArgumentToNewMessageInfo(code, newMessageInfoInvoke, protoMessageInfo);
  }

  private void rewriteInfoArgumentToNewMessageInfo(
      IRCode code, Value infoValue, ProtoMessageInfo protoMessageInfo) {
    infoValue.definition.replace(
        new ConstString(code.createValue(stringType), encoder.encodeInfo(protoMessageInfo)), code);
  }

  private void rewriteObjectsArgumentToNewMessageInfo(
      IRCode code,
      InvokeMethod newMessageInfoInvoke,
      ProtoMessageInfo protoMessageInfo) {
    // Position iterator immediately before the call to newMessageInfo().
    BasicBlock block = newMessageInfoInvoke.getBlock();
    InstructionListIterator instructionIterator = block.listIterator(code, newMessageInfoInvoke);
    Instruction previous = instructionIterator.previous();
    instructionIterator.setInsertionPosition(newMessageInfoInvoke.getPosition());
    assert previous == newMessageInfoInvoke;

    // Create the `objects` array.
    List<ProtoObject> objects = encoder.encodeObjects(protoMessageInfo);
    Value sizeValue =
        instructionIterator.insertConstIntInstruction(code, appView.options(), objects.size());
    Value newObjectsValue = code.createValue(objectArrayType);

    // Populate the `objects` array.
    var rewriteOptions = appView.options().rewriteArrayOptions();
    if (rewriteOptions.canUseFilledNewArrayOfNonStringObjects()
        && objects.size() < rewriteOptions.maxSizeForFilledNewArrayOfReferences) {
      List<Value> arrayValues = new ArrayList<>(objects.size());
      for (int i = 0; i < objects.size(); i++) {
        Instruction materializingInstruction = objects.get(i).buildIR(appView, code);
        instructionIterator.add(materializingInstruction);
        arrayValues.add(materializingInstruction.outValue());
      }
      instructionIterator.add(
          new InvokeNewArray(
              appView.dexItemFactory().objectArrayType, newObjectsValue, arrayValues));
    } else {
      instructionIterator.add(
          new NewArrayEmpty(newObjectsValue, sizeValue, appView.dexItemFactory().objectArrayType));

      for (int i = 0; i < objects.size(); i++) {
        Value indexValue =
            instructionIterator.insertConstIntInstruction(code, appView.options(), i);
        Instruction materializingInstruction = objects.get(i).buildIR(appView, code);
        instructionIterator.add(materializingInstruction);
        instructionIterator.add(
            ArrayPut.create(
                MemberType.OBJECT,
                newObjectsValue,
                indexValue,
                materializingInstruction.outValue()));
      }
    }

    // Pass the newly created `objects` array to RawMessageInfo.<init>(...) or
    // GeneratedMessageLite.newMessageInfo().
    setObjectsValueForMessageInfoConstructionInvoke(
        newMessageInfoInvoke, newObjectsValue, references);
  }

  public static InvokeMethod getNewMessageInfoInvoke(IRCode code, ProtoReferences references) {
    for (Instruction instruction : code.instructions()) {
      if (instruction.isInvokeMethod()) {
        InvokeMethod invoke = instruction.asInvokeMethod();
        if (references.isMessageInfoConstruction(invoke)) {
          return invoke;
        }
      }
    }
    return null;
  }
}
