// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldaccess;


import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessInfo;
import com.android.tools.r8.graph.FieldAccessInfoCollection;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
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
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class TrivialFieldAccessReprocessor {

  private final AppView<AppInfoWithLiveness> appView;
  private final PostMethodProcessor.Builder postMethodProcessorBuilder;

  /** Updated concurrently from {@link #processClass(DexProgramClass)}. */
  private final Set<DexEncodedField> fieldsOfInterest = Sets.newConcurrentHashSet();

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
    computeFieldsOfInterest();
    timing.end(); // Compute fields of interest

    if (fieldsOfInterest.isEmpty()) {
      timing.end(); // Trivial field accesses analysis
      return;
    }

    timing.begin("Enqueue methods for reprocessing");
    enqueueMethodsForReprocessing(appInfo, executorService);
    timing.end(); // Enqueue methods for reprocessing

    timing.begin("Clear reads from fields of interest");
    clearReadsFromFieldsOfInterest(appInfo);
    timing.end(); // Clear reads from fields of interest

    timing.end(); // Trivial field accesses analysis

    fieldsOfInterest.forEach(OptimizationFeedbackSimple.getInstance()::markFieldAsDead);
  }

  private void computeFieldsOfInterest() {
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      for (DexEncodedField field : clazz.instanceFields()) {
        if (canOptimizeField(field, appView)) {
          fieldsOfInterest.add(field);
        }
      }
      if (appView.canUseInitClass() || !clazz.classInitializationMayHaveSideEffects(appView)) {
        for (DexEncodedField field : clazz.staticFields()) {
          if (canOptimizeField(field, appView)) {
            fieldsOfInterest.add(field);
          }
        }
      }
    }
    assert verifyNoConstantFieldsOnSynthesizedClasses(appView);
  }

  private void clearReadsFromFieldsOfInterest(AppInfoWithLiveness appInfo) {
    FieldAccessInfoCollection<?> fieldAccessInfoCollection = appInfo.getFieldAccessInfoCollection();
    for (DexEncodedField field : fieldsOfInterest) {
      fieldAccessInfoCollection.get(field.field).asMutable().clearReads();
    }
  }

  private void enqueueMethodsForReprocessing(
      AppInfoWithLiveness appInfo, ExecutorService executorService) throws ExecutionException {
    ThreadUtils.processItems(appInfo.classes(), this::processClass, executorService);
    ThreadUtils.processItems(
        appInfo.getSyntheticItems().getPendingSyntheticClasses(),
        this::processClass,
        executorService);
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

    private boolean registerFieldAccess(DexField field, boolean isStatic) {
      DexEncodedField encodedField = appView.appInfo().resolveField(field).getResolvedField();
      if (encodedField != null) {
        // We cannot remove references from pass through functions.
        if (appView.isCfByteCodePassThrough(method.getDefinition())) {
          fieldsOfInterest.remove(encodedField);
          return true;
        }
        if (encodedField.isStatic() == isStatic) {
          if (fieldsOfInterest.contains(encodedField)) {
            methodsToReprocess.add(method);
          }
        } else {
          // Should generally not happen.
          fieldsOfInterest.remove(encodedField);
        }
      }
      return true;
    }

    @Override
    public void registerInstanceFieldWrite(DexField field) {
      registerFieldAccess(field, false);
    }

    @Override
    public void registerInstanceFieldRead(DexField field) {
      registerFieldAccess(field, false);
    }

    @Override
    public void registerStaticFieldRead(DexField field) {
      registerFieldAccess(field, true);
    }

    @Override
    public void registerStaticFieldWrite(DexField field) {
      registerFieldAccess(field, true);
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
