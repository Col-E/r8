// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldaccess;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.ir.optimize.info.OptimizationFeedback.getSimpleFeedback;
import static com.android.tools.r8.shaking.ObjectAllocationInfoCollectionUtils.mayHaveFinalizeMethodDirectlyOrIndirectly;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.dex.code.CfOrDexInstanceFieldRead;
import com.android.tools.r8.dex.code.CfOrDexStaticFieldRead;
import com.android.tools.r8.graph.AbstractAccessContexts;
import com.android.tools.r8.graph.AbstractAccessContexts.ConcreteAccessContexts;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessInfo;
import com.android.tools.r8.graph.FieldAccessInfoCollection;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeInstructionMetadata;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.ReferenceTypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.SingleFieldValue;
import com.android.tools.r8.ir.analysis.value.SingleValue;
import com.android.tools.r8.ir.conversion.PostMethodProcessor;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackDelayed;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.Sets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public final class TrivialFieldAccessReprocessor {

  enum FieldClassification {
    CONSTANT,
    NON_CONSTANT,
    UNKNOWN
  }

  private final AppView<AppInfoWithLiveness> appView;
  private final PostMethodProcessor.Builder postMethodProcessorBuilder;

  private final Map<DexEncodedField, ProgramMethodSet> dependencies = new ConcurrentHashMap<>();

  /** Updated concurrently from {@link #processClass(DexProgramClass)}. */
  private final Map<DexEncodedField, AbstractAccessContexts> readFields = new ConcurrentHashMap<>();

  /** Updated concurrently from {@link #processClass(DexProgramClass)}. */
  private final Map<DexEncodedField, AbstractAccessContexts> writtenFields =
      new ConcurrentHashMap<>();

  /** Updated concurrently from {@link #processClass(DexProgramClass)}. */
  private final Set<DexEncodedField> constantFields = Sets.newConcurrentHashSet();

  /** Updated concurrently from {@link #processClass(DexProgramClass)}. */
  private final Set<DexEncodedField> nonConstantFields = Sets.newConcurrentHashSet();

  /** Updated concurrently from {@link #processClass(DexProgramClass)}. */
  private final ProgramMethodSet methodsToReprocess = ProgramMethodSet.createConcurrent();

  public TrivialFieldAccessReprocessor(
      AppView<AppInfoWithLiveness> appView,
      PostMethodProcessor.Builder postMethodProcessorBuilder) {
    this.appView = appView;
    this.postMethodProcessorBuilder = postMethodProcessorBuilder;
  }

  private AppView<AppInfoWithLiveness> appView() {
    return appView;
  }

  public void run(
      ExecutorService executorService, OptimizationFeedbackDelayed feedback, Timing timing)
      throws ExecutionException {
    AppInfoWithLiveness appInfo = appView.appInfo();

    timing.begin("Trivial field accesses analysis");
    assert feedback.noUpdatesLeft();

    timing.begin("Compute fields of interest");
    computeFieldsWithNonTrivialValue();
    timing.end(); // Compute fields of interest

    timing.begin("Enqueue methods for reprocessing");
    enqueueMethodsForReprocessing(appInfo, executorService);
    timing.end(); // Enqueue methods for reprocessing

    timing.begin("Clear reads and writes from fields of interest");
    clearReadsAndWritesFromFieldsOfInterest(appInfo);
    timing.end(); // Clear reads from fields of interest
    timing.end(); // Trivial field accesses analysis

    constantFields.forEach(this::markFieldAsDead);
    readFields.keySet().forEach(this::markFieldAsDead);
    writtenFields.keySet().forEach(this::markWriteOnlyFieldAsDead);

    // Ensure determinism of method-to-reprocess set.
    appView.testing().checkDeterminism(postMethodProcessorBuilder::dump);
  }

  private void markWriteOnlyFieldAsDead(DexEncodedField field) {
    markFieldAsDead(field);
    getSimpleFeedback()
        .recordFieldHasAbstractValue(
            field, appView, appView.abstractValueFactory().createNullValue());
  }

  private void markFieldAsDead(DexEncodedField field) {
    // Don't mark pinned fields as dead, since they need to remain in the app even if all reads and
    // writes are removed.
    if (appView.appInfo().isPinned(field)) {
      assert field.getType().isAlwaysNull(appView);
    } else {
      getSimpleFeedback().markFieldAsDead(field);
    }
  }

  private void computeFieldsWithNonTrivialValue() {
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      for (DexEncodedField field : clazz.instanceFields()) {
        FieldClassification fieldClassification = classifyField(field, appView);
        switch (fieldClassification) {
          case CONSTANT:
            // Reprocess reads and writes.
            constantFields.add(field);
            break;
          case NON_CONSTANT:
            // Only reprocess writes, to allow branch pruning.
            nonConstantFields.add(field);
            break;
          default:
            assert fieldClassification == FieldClassification.UNKNOWN;
            break;
        }
      }
      if (appView.canUseInitClass() || !clazz.classInitializationMayHaveSideEffects(appView)) {
        for (DexEncodedField field : clazz.staticFields()) {
          FieldClassification fieldClassification = classifyField(field, appView);
          if (fieldClassification == FieldClassification.CONSTANT) {
            constantFields.add(field);
          } else {
            assert fieldClassification == FieldClassification.NON_CONSTANT
                || fieldClassification == FieldClassification.UNKNOWN;
          }
        }
      }
    }
    assert verifyNoConstantFieldsOnSynthesizedClasses(appView);
  }

  private void clearReadsAndWritesFromFieldsOfInterest(AppInfoWithLiveness appInfo) {
    FieldAccessInfoCollection<?> fieldAccessInfoCollection = appInfo.getFieldAccessInfoCollection();
    for (DexEncodedField field : constantFields) {
      fieldAccessInfoCollection.get(field.getReference()).asMutable().clearReads();
    }
    for (DexEncodedField field : readFields.keySet()) {
      fieldAccessInfoCollection.get(field.getReference()).asMutable().clearWrites();
    }
    for (DexEncodedField field : writtenFields.keySet()) {
      fieldAccessInfoCollection.get(field.getReference()).asMutable().clearReads();
    }
  }

  private void enqueueMethodsForReprocessing(
      AppInfoWithLiveness appInfo, ExecutorService executorService) throws ExecutionException {
    ThreadUtils.processItems(appInfo.classes(), this::processClass, executorService);
    ThreadUtils.processItems(
        appInfo.getSyntheticItems().getPendingSyntheticClasses(),
        this::processClass,
        executorService);
    processFieldsNeverRead(appInfo);
    processFieldsNeverWritten(appInfo);
    postMethodProcessorBuilder.rewrittenWithLens(appView.graphLens()).put(methodsToReprocess);
  }

  private void processClass(DexProgramClass clazz) {
    clazz.forEachProgramMethodMatching(
        DexEncodedMethod::hasCode,
        method -> {
          method.registerCodeReferences(new TrivialFieldAccessUseRegistry(method));
          method.getDefinition().getCode().clearMetadata();
        });
  }

  private static FieldClassification classifyField(
      DexEncodedField field, AppView<AppInfoWithLiveness> appView) {
    FieldAccessInfo fieldAccessInfo =
        appView.appInfo().getFieldAccessInfoCollection().get(field.getReference());
    if (fieldAccessInfo == null
        || fieldAccessInfo.hasReflectiveAccess()
        || fieldAccessInfo.isAccessedFromMethodHandle()
        || fieldAccessInfo.isReadFromAnnotation()) {
      return FieldClassification.UNKNOWN;
    }
    AbstractValue abstractValue = field.getOptimizationInfo().getAbstractValue();
    if (abstractValue.isSingleValue()) {
      SingleValue singleValue = abstractValue.asSingleValue();
      if (!singleValue.isMaterializableInAllContexts(appView)) {
        return FieldClassification.UNKNOWN;
      }
      if (singleValue.isSingleConstValue()) {
        return FieldClassification.CONSTANT;
      }
      if (singleValue.isSingleFieldValue()) {
        SingleFieldValue singleFieldValue = singleValue.asSingleFieldValue();
        DexField singleField = singleFieldValue.getField();
        if (singleField != field.getReference()
            && !singleFieldValue.mayHaveFinalizeMethodDirectlyOrIndirectly(appView)) {
          return FieldClassification.CONSTANT;
        }
      }
      return FieldClassification.UNKNOWN;
    }
    if (abstractValue.isNonConstantNumberValue()) {
      return FieldClassification.NON_CONSTANT;
    }
    return FieldClassification.UNKNOWN;
  }

  private void processFieldsNeverRead(AppInfoWithLiveness appInfo) {
    FieldAccessInfoCollection<?> fieldAccessInfoCollection = appInfo.getFieldAccessInfoCollection();
    writtenFields
        .entrySet()
        .removeIf(
            entry ->
                !entry.getValue().isConcrete()
                    || !canOptimizeOnlyReadOrWrittenField(
                        entry.getKey(), true, fieldAccessInfoCollection));
    writtenFields.forEach(
        (field, contexts) -> {
          assert !readFields.containsKey(field);
          fieldAccessInfoCollection.get(field.getReference()).asMutable().clearReads();
          methodsToReprocess.addAll(
              contexts.asConcrete().getAccessesWithContexts().values().iterator().next());
          methodsToReprocess.addAll(dependencies.getOrDefault(field, ProgramMethodSet.empty()));
        });
  }

  private void processFieldsNeverWritten(AppInfoWithLiveness appInfo) {
    FieldAccessInfoCollection<?> fieldAccessInfoCollection = appInfo.getFieldAccessInfoCollection();
    readFields
        .entrySet()
        .removeIf(
            entry ->
                !entry.getValue().isConcrete()
                    || !canOptimizeOnlyReadOrWrittenField(
                        entry.getKey(), false, fieldAccessInfoCollection));
    readFields.forEach(
        (field, contexts) -> {
          assert !writtenFields.containsKey(field);
          methodsToReprocess.addAll(
              contexts.asConcrete().getAccessesWithContexts().values().iterator().next());
          methodsToReprocess.addAll(dependencies.getOrDefault(field, ProgramMethodSet.empty()));
        });
  }

  private boolean canOptimizeOnlyReadOrWrittenField(
      DexEncodedField field,
      boolean isWrite,
      FieldAccessInfoCollection<?> fieldAccessInfoCollection) {
    assert !appView.appInfo().isPinned(field) || field.getType().isAlwaysNull(appView);

    FieldAccessInfo fieldAccessInfo = fieldAccessInfoCollection.get(field.getReference());
    if (fieldAccessInfo == null) {
      assert false
          : "Expected program field with concrete accesses to be present in field access "
              + "collection";
      return false;
    }

    if (fieldAccessInfo.hasReflectiveAccess()
        || fieldAccessInfo.isAccessedFromMethodHandle()
        || fieldAccessInfo.isReadFromRecordInvokeDynamic()
        || fieldAccessInfo.isReadFromAnnotation()) {
      return false;
    }

    if (isWrite && field.getType().isReferenceType()) {
      ReferenceTypeElement fieldType = field.getTypeElement(appView).asReferenceType();
      ClassTypeElement classType =
          (fieldType.isArrayType() ? fieldType.asArrayType().getBaseType() : fieldType)
              .asClassType();
      if (classType != null && mayHaveFinalizeMethodDirectlyOrIndirectly(appView, classType)) {
        return false;
      }
    }

    return true;
  }

  private static boolean verifyNoConstantFieldsOnSynthesizedClasses(
      AppView<AppInfoWithLiveness> appView) {
    for (DexProgramClass clazz :
        appView.appInfo().getSyntheticItems().getPendingSyntheticClasses()) {
      for (DexEncodedField field : clazz.fields()) {
        assert field.getOptimizationInfo().getAbstractValue().isUnknown();
      }
    }
    return true;
  }

  class TrivialFieldAccessUseRegistry extends UseRegistry<ProgramMethod> {

    TrivialFieldAccessUseRegistry(ProgramMethod method) {
      super(appView(), method);
    }

    private void registerFieldAccess(
        DexField reference,
        boolean isStatic,
        boolean isWrite,
        BytecodeInstructionMetadata metadata) {
      FieldResolutionResult resolutionResult = appView().appInfo().resolveField(reference);
      if (!resolutionResult.hasProgramResult()) {
        // We don't care about field accesses that may not resolve to a program field.
        return;
      }

      ProgramField field = resolutionResult.getProgramField();
      DexEncodedField definition = field.getDefinition();

      if (definition.isStatic() != isStatic
          || appView.isCfByteCodePassThrough(getContext().getDefinition())
          || !resolutionResult.isSingleProgramFieldResolutionResult()
          || resolutionResult.isAccessibleFrom(getContext(), appView()).isPossiblyFalse()
          || appView().appInfo().isNeverReprocessMethod(getContext())) {
        recordAccessThatCannotBeOptimized(field, definition);
        return;
      }

      if (metadata != null) {
        if (isUnusedReadAfterMethodStaticizing(field, metadata)) {
          // Ignore this read.
          dependencies
              .computeIfAbsent(field.getDefinition(), ignoreKey(ProgramMethodSet::createConcurrent))
              .add(getContext());
          return;
        }
        if (metadata.isReadForWrite()) {
          // Ignore this read. If the field ends up only being written, then we will still reprocess
          // the method with the read-for-write instruction, since the method contains a write that
          // requires reprocessing.
          return;
        }
      }

      // Record access.
      if (field.isProgramField() && appView().appInfo().mayPropagateValueFor(appView(), field)) {
        if (field.getAccessFlags().isStatic() == isStatic) {
          if (isWrite) {
            recordFieldAccessContext(definition, writtenFields, readFields);
          } else {
            recordFieldAccessContext(definition, readFields, writtenFields);
          }
        } else {
          destroyFieldAccessContexts(definition);
        }
      }

      if (constantFields.contains(definition)
          || (!isWrite && nonConstantFields.contains(definition))) {
        methodsToReprocess.add(getContext());
      }
    }

    private boolean isUnusedReadAfterMethodStaticizing(
        DexClassAndField field, BytecodeInstructionMetadata metadata) {
      if (!metadata.isReadForInvokeReceiver()
          || field.getOptimizationInfo().getDynamicType().getNullability().isMaybeNull()) {
        return false;
      }
      Set<DexMethod> readForInvokeReceiver = metadata.getReadForInvokeReceiver();
      for (DexMethod methodReference : readForInvokeReceiver) {
        DexMethod rewrittenMethodReference =
            appView.graphLens().getRenamedMethodSignature(methodReference, appView.codeLens());
        DexProgramClass holder =
            asProgramClassOrNull(appView.definitionFor(rewrittenMethodReference.getHolderType()));
        ProgramMethod method = rewrittenMethodReference.lookupOnProgramClass(holder);
        if (method == null) {
          assert false;
          return false;
        }
        if (!method.getDefinition().isStatic()) {
          return false;
        }
      }
      return true;
    }

    private void recordAccessThatCannotBeOptimized(
        DexClassAndField field, DexEncodedField definition) {
      constantFields.remove(definition);
      if (field.isProgramField() && appView().appInfo().mayPropagateValueFor(appView(), field)) {
        destroyFieldAccessContexts(definition);
      }
    }

    private void recordFieldAccessContext(
        DexEncodedField field,
        Map<DexEncodedField, AbstractAccessContexts> fieldAccesses,
        Map<DexEncodedField, AbstractAccessContexts> otherFieldAccesses) {
      synchronized (field) {
        AbstractAccessContexts otherAccessContexts =
            otherFieldAccesses.getOrDefault(field, AbstractAccessContexts.empty());
        if (otherAccessContexts.isBottom()) {
          // Only read or written.
          AbstractAccessContexts accessContexts =
              fieldAccesses.computeIfAbsent(field, ignore -> new ConcreteAccessContexts());
          assert accessContexts.isConcrete();
          accessContexts.asConcrete().recordAccess(field.getReference(), getContext());
        } else if (!otherAccessContexts.isTop()) {
          // Now both read and written.
          fieldAccesses.put(field, AbstractAccessContexts.unknown());
          otherFieldAccesses.put(field, AbstractAccessContexts.unknown());
        } else {
          // Already read and written.
          assert fieldAccesses.getOrDefault(field, AbstractAccessContexts.empty()).isTop();
          assert otherFieldAccesses.getOrDefault(field, AbstractAccessContexts.empty()).isTop();
        }
      }
    }

    private void destroyFieldAccessContexts(DexEncodedField field) {
      synchronized (field) {
        readFields.put(field, AbstractAccessContexts.unknown());
        writtenFields.put(field, AbstractAccessContexts.unknown());
      }
    }

    @Override
    public void registerInstanceFieldReadWithMetadata(
        DexField field, BytecodeInstructionMetadata metadata) {
      registerFieldAccess(field, false, false, metadata);
    }

    @Override
    public void registerInstanceFieldRead(DexField field) {
      registerInstanceFieldReadWithMetadata(field, BytecodeInstructionMetadata.none());
    }

    @Override
    public void registerInstanceFieldReadInstruction(CfOrDexInstanceFieldRead instruction) {
      BytecodeInstructionMetadata metadata =
          getContext().getDefinition().getCode().getMetadata(instruction);
      registerInstanceFieldReadWithMetadata(instruction.getField(), metadata);
    }

    @Override
    public void registerInstanceFieldWrite(DexField field) {
      registerFieldAccess(field, false, true, BytecodeInstructionMetadata.none());
    }

    @Override
    public void registerStaticFieldReadWithMetadata(
        DexField field, BytecodeInstructionMetadata metadata) {
      registerFieldAccess(field, true, false, metadata);
    }

    @Override
    public void registerStaticFieldRead(DexField field) {
      registerStaticFieldReadWithMetadata(field, BytecodeInstructionMetadata.none());
    }

    @Override
    public void registerStaticFieldReadInstruction(CfOrDexStaticFieldRead instruction) {
      BytecodeInstructionMetadata metadata =
          getContext().getDefinition().getCode().getMetadata(instruction);
      registerStaticFieldReadWithMetadata(instruction.getField(), metadata);
    }

    @Override
    public void registerStaticFieldWrite(DexField field) {
      registerFieldAccess(field, true, true, BytecodeInstructionMetadata.none());
    }

    @Override
    public void registerInitClass(DexType clazz) {}

    @Override
    public void registerInvokeVirtual(DexMethod method) {}

    @Override
    public void registerInvokeDirect(DexMethod method) {}

    @Override
    public void registerInvokeStatic(DexMethod method) {}

    @Override
    public void registerInvokeInterface(DexMethod method) {}

    @Override
    public void registerInvokeSuper(DexMethod method) {}

    @Override
    public void registerNewInstance(DexType type) {}

    @Override
    public void registerTypeReference(DexType type) {}

    @Override
    public void registerInstanceOf(DexType type) {}
  }
}
