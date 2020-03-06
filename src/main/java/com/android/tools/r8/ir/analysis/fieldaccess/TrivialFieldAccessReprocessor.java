// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldaccess;

import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.FieldAccessInfo;
import com.android.tools.r8.graph.FieldAccessInfoCollection;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.analysis.type.ClassTypeLatticeElement;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.SingleFieldValue;
import com.android.tools.r8.ir.analysis.value.SingleValue;
import com.android.tools.r8.ir.conversion.PostMethodProcessor;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackDelayed;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackSimple;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
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
  private final Set<DexEncodedMethod> methodsToReprocess = Sets.newConcurrentHashSet();

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
    computeFieldsOfInterest(appInfo);
    timing.end(); // Compute fields of interest

    if (fieldsOfInterest.isEmpty()) {
      timing.end(); // Trivial field accesses analysis
      return;
    }

    timing.begin("Clear reads from fields of interest");
    clearReadsFromFieldsOfInterest(appInfo);
    timing.end(); // Clear reads from fields of interest

    timing.begin("Enqueue methods for reprocessing");
    enqueueMethodsForReprocessing(appInfo, executorService);
    timing.end(); // Enqueue methods for reprocessing
    timing.end(); // Trivial field accesses analysis

    fieldsOfInterest.forEach(OptimizationFeedbackSimple.getInstance()::markFieldAsDead);
  }

  private void computeFieldsOfInterest(AppInfoWithLiveness appInfo) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    for (DexProgramClass clazz : appInfo.classes()) {
      for (DexEncodedField field : clazz.instanceFields()) {
        if (canOptimizeField(field, appView)) {
          fieldsOfInterest.add(field);
        }
      }
      OptionalBool mayRequireClinitField = OptionalBool.unknown();
      for (DexEncodedField field : clazz.staticFields()) {
        if (canOptimizeField(field, appView)) {
          if (mayRequireClinitField.isUnknown()) {
            mayRequireClinitField =
                OptionalBool.of(clazz.classInitializationMayHaveSideEffects(appView));
          }
          fieldsOfInterest.add(field);
        }
      }
      if (mayRequireClinitField.isTrue()) {
        DexField clinitField = dexItemFactory.objectMembers.clinitField;
        if (clazz.lookupStaticField(dexItemFactory.objectMembers.clinitField) == null) {
          FieldAccessFlags accessFlags =
              FieldAccessFlags.fromSharedAccessFlags(
                  Constants.ACC_SYNTHETIC | Constants.ACC_PUBLIC | Constants.ACC_STATIC);
          clazz.appendStaticField(
              new DexEncodedField(
                  dexItemFactory.createField(clazz.type, clinitField.type, clinitField.name),
                  accessFlags,
                  DexAnnotationSet.empty(),
                  null));
          appView.appInfo().invalidateTypeCacheFor(clazz.type);
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
    ThreadUtils.processItems(appInfo.synthesizedClasses(), this::processClass, executorService);
    postMethodProcessorBuilder.put(methodsToReprocess);
  }

  private void processClass(DexProgramClass clazz) {
    for (DexEncodedMethod method : clazz.methods()) {
      if (method.hasCode()) {
        method.getCode().registerCodeReferences(method, new TrivialFieldAccessUseRegistry(method));
      }
    }
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
        if (singleField == field.field) {
          return false;
        }
        if (singleField.type.isClassType()) {
          ClassTypeLatticeElement fieldType =
              TypeLatticeElement.fromDexType(singleFieldValue.getField().type, maybeNull(), appView)
                  .asClassTypeLatticeElement();
          return !appView.appInfo().mayHaveFinalizeMethodDirectlyOrIndirectly(fieldType);
        }
        return true;
      }
    }
    return false;
  }

  private static boolean verifyNoConstantFieldsOnSynthesizedClasses(
      AppView<AppInfoWithLiveness> appView) {
    for (DexProgramClass clazz : appView.appInfo().synthesizedClasses()) {
      for (DexEncodedField field : clazz.fields()) {
        assert field.getOptimizationInfo().getAbstractValue().isUnknown();
      }
    }
    return true;
  }

  class TrivialFieldAccessUseRegistry extends UseRegistry {

    private final DexEncodedMethod method;

    TrivialFieldAccessUseRegistry(DexEncodedMethod method) {
      super(appView.dexItemFactory());
      this.method = method;
    }

    private boolean registerFieldAccess(DexField field, boolean isStatic) {
      DexEncodedField encodedField = appView.appInfo().resolveField(field);
      if (encodedField != null) {
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
    public boolean registerInstanceFieldWrite(DexField field) {
      return registerFieldAccess(field, false);
    }

    @Override
    public boolean registerInstanceFieldRead(DexField field) {
      return registerFieldAccess(field, false);
    }

    @Override
    public boolean registerStaticFieldRead(DexField field) {
      return registerFieldAccess(field, true);
    }

    @Override
    public boolean registerStaticFieldWrite(DexField field) {
      return registerFieldAccess(field, true);
    }

    @Override
    public boolean registerInvokeVirtual(DexMethod method) {
      return false;
    }

    @Override
    public boolean registerInvokeDirect(DexMethod method) {
      return false;
    }

    @Override
    public boolean registerInvokeStatic(DexMethod method) {
      return false;
    }

    @Override
    public boolean registerInvokeInterface(DexMethod method) {
      return false;
    }

    @Override
    public boolean registerInvokeSuper(DexMethod method) {
      return false;
    }

    @Override
    public boolean registerNewInstance(DexType type) {
      return false;
    }

    @Override
    public boolean registerTypeReference(DexType type) {
      return false;
    }
  }
}
