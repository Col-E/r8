// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldaccess;

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
import com.android.tools.r8.graph.FieldResolutionResult.SuccessfulFieldResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.ReferenceTypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.SingleFieldValue;
import com.android.tools.r8.ir.analysis.value.SingleValue;
import com.android.tools.r8.ir.conversion.PostMethodProcessor;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackDelayed;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackSimple;
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

public class TrivialFieldAccessReprocessor {

  private final AppView<AppInfoWithLiveness> appView;
  private final PostMethodProcessor.Builder postMethodProcessorBuilder;

  /** Updated concurrently from {@link #processClass(DexProgramClass)}. */
  private final Map<DexEncodedField, AbstractAccessContexts> readFields = new ConcurrentHashMap<>();

  /** Updated concurrently from {@link #processClass(DexProgramClass)}. */
  private final Map<DexEncodedField, AbstractAccessContexts> writtenFields =
      new ConcurrentHashMap<>();

  /** Updated concurrently from {@link #processClass(DexProgramClass)}. */
  private final Set<DexEncodedField> constantFields = Sets.newConcurrentHashSet();

  /** Updated concurrently from {@link #processClass(DexProgramClass)}. */
  private final ProgramMethodSet methodsToReprocess = ProgramMethodSet.createConcurrent();

  public TrivialFieldAccessReprocessor(
      AppView<AppInfoWithLiveness> appView,
      PostMethodProcessor.Builder postMethodProcessorBuilder) {
    this.appView = appView;
    this.postMethodProcessorBuilder = postMethodProcessorBuilder;
  }

  public void run(
      ExecutorService executorService, OptimizationFeedbackDelayed feedback, Timing timing)
      throws ExecutionException {
    AppInfoWithLiveness appInfo = appView.appInfo();

    timing.begin("Trivial field accesses analysis");
    assert feedback.noUpdatesLeft();

    timing.begin("Compute fields of interest");
    computeConstantFields();
    timing.end(); // Compute fields of interest

    timing.begin("Enqueue methods for reprocessing");
    enqueueMethodsForReprocessing(appInfo, executorService);
    timing.end(); // Enqueue methods for reprocessing

    timing.begin("Clear reads and writes from fields of interest");
    clearReadsAndWritesFromFieldsOfInterest(appInfo);
    timing.end(); // Clear reads from fields of interest
    timing.end(); // Trivial field accesses analysis

    constantFields.forEach(OptimizationFeedbackSimple.getInstance()::markFieldAsDead);
    readFields.keySet().forEach(OptimizationFeedbackSimple.getInstance()::markFieldAsDead);
    writtenFields.keySet().forEach(OptimizationFeedbackSimple.getInstance()::markFieldAsDead);
  }

  private void computeConstantFields() {
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      for (DexEncodedField field : clazz.instanceFields()) {
        if (canOptimizeField(field, appView)) {
          constantFields.add(field);
        }
      }
      if (appView.canUseInitClass() || !clazz.classInitializationMayHaveSideEffects(appView)) {
        for (DexEncodedField field : clazz.staticFields()) {
          if (canOptimizeField(field, appView)) {
            constantFields.add(field);
          }
        }
      }
    }
    assert verifyNoConstantFieldsOnSynthesizedClasses(appView);
  }

  private void clearReadsAndWritesFromFieldsOfInterest(AppInfoWithLiveness appInfo) {
    FieldAccessInfoCollection<?> fieldAccessInfoCollection = appInfo.getFieldAccessInfoCollection();
    for (DexEncodedField field : constantFields) {
      fieldAccessInfoCollection.get(field.field).asMutable().clearReads();
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
    postMethodProcessorBuilder.put(methodsToReprocess);
  }

  private void processClass(DexProgramClass clazz) {
    clazz.forEachProgramMethodMatching(
        DexEncodedMethod::hasCode,
        method -> method.registerCodeReferences(new TrivialFieldAccessUseRegistry(method)));
  }

  private static boolean canOptimizeField(
      DexEncodedField field, AppView<AppInfoWithLiveness> appView) {
    FieldAccessInfo fieldAccessInfo =
        appView.appInfo().getFieldAccessInfoCollection().get(field.field);
    if (fieldAccessInfo == null || fieldAccessInfo.isAccessedFromMethodHandle()) {
      return false;
    }
    AbstractValue abstractValue = field.getOptimizationInfo().getAbstractValue();
    if (abstractValue.isSingleValue()) {
      SingleValue singleValue = abstractValue.asSingleValue();
      if (!singleValue.isMaterializableInAllContexts(appView)) {
        return false;
      }
      if (singleValue.isSingleConstValue()) {
        return true;
      }
      if (singleValue.isSingleFieldValue()) {
        SingleFieldValue singleFieldValue = singleValue.asSingleFieldValue();
        DexField singleField = singleFieldValue.getField();
        return singleField != field.field
            && !singleFieldValue.mayHaveFinalizeMethodDirectlyOrIndirectly(appView);
      }
    }
    return false;
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
        });
  }

  private boolean canOptimizeOnlyReadOrWrittenField(
      DexEncodedField field,
      boolean isWrite,
      FieldAccessInfoCollection<?> fieldAccessInfoCollection) {
    assert !appView.appInfo().isPinned(field);
    FieldAccessInfo fieldAccessInfo = fieldAccessInfoCollection.get(field.getReference());
    if (fieldAccessInfo == null) {
      assert false
          : "Expected program field with concrete accesses to be present in field access "
              + "collection";
      return false;
    }

    if (fieldAccessInfo.hasReflectiveAccess()
        || fieldAccessInfo.isAccessedFromMethodHandle()
        || fieldAccessInfo.isReadFromAnnotation()
        || appView.appInfo().getSyntheticItems().isSyntheticClass(field.getHolderType())) {
      return false;
    }

    if (isWrite && field.getType().isReferenceType()) {
      ReferenceTypeElement fieldType = field.getTypeElement(appView).asReferenceType();
      ClassTypeElement classType =
          (fieldType.isArrayType() ? fieldType.asArrayType().getBaseType() : fieldType)
              .asClassType();
      if (classType != null
          && appView.appInfo().mayHaveFinalizeMethodDirectlyOrIndirectly(classType)) {
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

  class TrivialFieldAccessUseRegistry extends UseRegistry {

    private final ProgramMethod method;

    TrivialFieldAccessUseRegistry(ProgramMethod method) {
      super(appView.dexItemFactory());
      this.method = method;
    }

    private void registerFieldAccess(DexField reference, boolean isStatic, boolean isWrite) {
      SuccessfulFieldResolutionResult resolutionResult =
          appView.appInfo().resolveField(reference).asSuccessfulResolution();
      if (resolutionResult == null) {
        return;
      }

      DexClassAndField field = resolutionResult.getResolutionPair();
      DexEncodedField definition = field.getDefinition();

      // Record access.
      if (field.isProgramField() && appView.appInfo().mayPropagateValueFor(field)) {
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

      // We cannot remove references from pass through functions.
      if (appView.isCfByteCodePassThrough(method.getDefinition())) {
        constantFields.remove(definition);
        return;
      }

      if (definition.isStatic() == isStatic) {
        if (constantFields.contains(definition)) {
          methodsToReprocess.add(method);
        }
      } else {
        // Should generally not happen.
        constantFields.remove(definition);
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
          accessContexts.asConcrete().recordAccess(field.getReference(), method);
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
    public void registerInstanceFieldRead(DexField field) {
      registerFieldAccess(field, false, false);
    }

    @Override
    public void registerInstanceFieldWrite(DexField field) {
      registerFieldAccess(field, false, true);
    }

    @Override
    public void registerStaticFieldRead(DexField field) {
      registerFieldAccess(field, true, false);
    }

    @Override
    public void registerStaticFieldWrite(DexField field) {
      registerFieldAccess(field, true, true);
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
