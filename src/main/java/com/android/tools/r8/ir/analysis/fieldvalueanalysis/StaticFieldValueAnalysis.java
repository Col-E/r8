// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldvalueanalysis;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.ir.analysis.value.ObjectState;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.ClassInitializerDefaultsOptimization.ClassInitializerDefaultsResult;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Timing;

public class StaticFieldValueAnalysis extends FieldValueAnalysis {

  private StaticFieldValueAnalysis(
      AppView<AppInfoWithLiveness> appView,
      IRCode code,
      OptimizationFeedback feedback,
      DexProgramClass clazz,
      DexEncodedMethod method) {
    super(appView, code, feedback, clazz, method);
  }

  public static void run(
      AppView<?> appView,
      IRCode code,
      ClassInitializerDefaultsResult classInitializerDefaultsResult,
      OptimizationFeedback feedback,
      DexEncodedMethod method,
      Timing timing) {
    assert appView.appInfo().hasLiveness();
    assert appView.enableWholeProgramOptimizations();
    assert method.isClassInitializer();
    timing.begin("Analyze class initializer");
    DexProgramClass clazz = appView.definitionFor(method.holder()).asProgramClass();
    new StaticFieldValueAnalysis(appView.withLiveness(), code, feedback, clazz, method)
        .computeFieldOptimizationInfo(classInitializerDefaultsResult);
    timing.end();
  }

  @Override
  void computeFieldOptimizationInfo(ClassInitializerDefaultsResult classInitializerDefaultsResult) {
    super.computeFieldOptimizationInfo(classInitializerDefaultsResult);

    classInitializerDefaultsResult.forEachOptimizedField(
        (field, value) -> {
          if (putsPerField.containsKey(field)
              || !appView.appInfo().isFieldOnlyWrittenInMethod(field, method)) {
            return;
          }

          AbstractValueFactory factory = appView.abstractValueFactory();
          if (value.isDexValueNumber()) {
            feedback.recordFieldHasAbstractValue(
                field,
                appView,
                factory.createSingleNumberValue(value.asDexValueNumber().getRawValue()));
          } else if (value.isDexValueString()) {
            feedback.recordFieldHasAbstractValue(
                field,
                appView,
                factory.createSingleStringValue(value.asDexValueString().getValue()));
          } else if (value.isDexItemBasedValueString()) {
            // TODO(b/150835624): Extend to dex item based const strings.
          } else {
            assert false : value.getClass().getName();
          }
        });
  }

  @Override
  boolean isSubjectToOptimization(DexEncodedField field) {
    return field.isStatic()
        && field.holder() == clazz.type
        && appView.appInfo().isFieldOnlyWrittenInMethod(field, method);
  }

  @Override
  void updateFieldOptimizationInfo(DexEncodedField field, FieldInstruction fieldPut, Value value) {
    // Abstract value.
    Value root = value.getAliasedValue();
    AbstractValue abstractValue = root.getAbstractValue(appView, clazz.type);
    if (abstractValue.isUnknown()) {
      feedback.recordFieldHasAbstractValue(
          field,
          appView,
          appView.abstractValueFactory().createSingleFieldValue(field.field, ObjectState.empty()));
    } else {
      feedback.recordFieldHasAbstractValue(field, appView, abstractValue);
    }

    // Dynamic upper bound type.
    TypeElement fieldType =
        TypeElement.fromDexType(field.field.type, Nullability.maybeNull(), appView);
    TypeElement dynamicUpperBoundType = value.getDynamicUpperBoundType(appView);
    if (dynamicUpperBoundType.strictlyLessThan(fieldType, appView)) {
      feedback.markFieldHasDynamicUpperBoundType(field, dynamicUpperBoundType);
    }

    // Dynamic lower bound type.
    ClassTypeElement dynamicLowerBoundType = value.getDynamicLowerBoundType(appView);
    if (dynamicLowerBoundType != null) {
      assert dynamicLowerBoundType.lessThanOrEqual(dynamicUpperBoundType, appView);
      feedback.markFieldHasDynamicLowerBoundType(field, dynamicLowerBoundType);
    }
  }
}
