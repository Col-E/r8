// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldvalueanalysis;

import static com.android.tools.r8.ir.code.Opcodes.*;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.ir.analysis.type.ClassTypeLatticeElement;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.SingleEnumValue;
import com.android.tools.r8.ir.analysis.value.UnknownValue;
import com.android.tools.r8.ir.code.ArrayPut;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.ClassInitializerDefaultsOptimization.ClassInitializerDefaultsResult;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

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
      DexEncodedMethod method) {
    assert appView.appInfo().hasLiveness();
    assert appView.enableWholeProgramOptimizations();
    assert method.isClassInitializer();
    DexProgramClass clazz = appView.definitionFor(method.method.holder).asProgramClass();
    new StaticFieldValueAnalysis(appView.withLiveness(), code, feedback, clazz, method)
        .computeFieldOptimizationInfo(classInitializerDefaultsResult);
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
    AbstractValue abstractValue = computeAbstractValue(root);
    if (abstractValue.isUnknown()) {
      feedback.recordFieldHasAbstractValue(
          field, appView, appView.abstractValueFactory().createSingleFieldValue(field.field));
    } else {
      feedback.recordFieldHasAbstractValue(field, appView, abstractValue);
    }

    // Dynamic upper bound type.
    TypeLatticeElement fieldType =
        TypeLatticeElement.fromDexType(field.field.type, Nullability.maybeNull(), appView);
    TypeLatticeElement dynamicUpperBoundType = value.getDynamicUpperBoundType(appView);
    if (dynamicUpperBoundType.strictlyLessThan(fieldType, appView)) {
      feedback.markFieldHasDynamicUpperBoundType(field, dynamicUpperBoundType);
    }

    // Dynamic lower bound type.
    ClassTypeLatticeElement dynamicLowerBoundType = value.getDynamicLowerBoundType(appView);
    if (dynamicLowerBoundType != null) {
      assert dynamicLowerBoundType.lessThanOrEqual(dynamicUpperBoundType, appView);
      feedback.markFieldHasDynamicLowerBoundType(field, dynamicLowerBoundType);
    }
  }

  private AbstractValue computeAbstractValue(Value value) {
    assert !value.hasAliasedValue();
    if (clazz.isEnum()) {
      SingleEnumValue singleEnumValue = getSingleEnumValue(value);
      if (singleEnumValue != null) {
        return singleEnumValue;
      }
    }
    if (!value.isPhi()) {
      return value.definition.getAbstractValue(appView, clazz.type);
    }
    return UnknownValue.getInstance();
  }

  /**
   * If {@param value} is defined by a new-instance instruction that instantiates the enclosing enum
   * class, and the value is assigned into exactly one static enum field on the enclosing enum
   * class, then returns a {@link SingleEnumValue} instance. Otherwise, returns {@code null}.
   *
   * <p>Note that enum constructors also store the newly instantiated enums in the {@code $VALUES}
   * array field on the enum. Therefore, this code also allows {@param value} to be stored into an
   * array as long as the array is identified as being the {@code $VALUES} array.
   */
  private SingleEnumValue getSingleEnumValue(Value value) {
    assert clazz.isEnum();
    assert !value.hasAliasedValue();
    if (value.isPhi() || !value.definition.isNewInstance()) {
      return null;
    }

    NewInstance newInstance = value.definition.asNewInstance();
    if (newInstance.clazz != clazz.type) {
      return null;
    }

    if (value.hasDebugUsers() || value.hasPhiUsers()) {
      return null;
    }

    DexEncodedField enumField = null;
    for (Instruction user : value.uniqueUsers()) {
      switch (user.opcode()) {
        case ARRAY_PUT:
          // Check that this is assigning the enum into the enum values array.
          ArrayPut arrayPut = user.asArrayPut();
          if (arrayPut.value().getAliasedValue() != value || !isEnumValuesArray(arrayPut.array())) {
            return null;
          }
          break;

        case INVOKE_DIRECT:
          // Check that this is the corresponding constructor call.
          InvokeDirect invoke = user.asInvokeDirect();
          if (!appView.dexItemFactory().isConstructor(invoke.getInvokedMethod())
              || invoke.getReceiver() != value) {
            return null;
          }
          break;

        case STATIC_PUT:
          DexEncodedField field = clazz.lookupStaticField(user.asStaticPut().getField());
          if (field != null && field.accessFlags.isEnum()) {
            if (enumField != null) {
              return null;
            }
            enumField = field;
          }
          break;

        default:
          return null;
      }
    }

    if (enumField == null) {
      return null;
    }

    return appView.abstractValueFactory().createSingleEnumValue(enumField.field);
  }

  private boolean isEnumValuesArray(Value value) {
    assert clazz.isEnum();
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    DexField valuesField =
        dexItemFactory.createField(
            clazz.type,
            clazz.type.toArrayType(1, dexItemFactory),
            dexItemFactory.enumValuesFieldName);

    Value root = value.getAliasedValue();
    if (root.isPhi()) {
      return false;
    }

    Instruction definition = root.definition;
    if (definition.isNewArrayEmpty()) {
      for (Instruction user : root.aliasedUsers()) {
        if (user.isStaticPut() && user.asStaticPut().getField() == valuesField) {
          return true;
        }
      }
    } else if (definition.isStaticGet()) {
      return definition.asStaticGet().getField() == valuesField;
    }

    return false;
  }
}
