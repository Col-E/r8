// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldvalueanalysis;

import static com.android.tools.r8.ir.code.Opcodes.ARRAY_PUT;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_DIRECT;
import static com.android.tools.r8.ir.code.Opcodes.STATIC_PUT;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.ir.analysis.value.ObjectState;
import com.android.tools.r8.ir.analysis.value.SingleFieldValue;
import com.android.tools.r8.ir.code.ArrayPut;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.ClassInitializerDefaultsOptimization.ClassInitializerDefaultsResult;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldArgumentInitializationInfo;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfoCollection;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Timing;

public class StaticFieldValueAnalysis extends FieldValueAnalysis {

  private StaticFieldValueAnalysis(
      AppView<AppInfoWithLiveness> appView, IRCode code, OptimizationFeedback feedback) {
    super(appView, code, feedback);
  }

  public static void run(
      AppView<?> appView,
      IRCode code,
      ClassInitializerDefaultsResult classInitializerDefaultsResult,
      OptimizationFeedback feedback,
      Timing timing) {
    assert appView.appInfo().hasLiveness();
    assert appView.enableWholeProgramOptimizations();
    assert code.context().getDefinition().isClassInitializer();
    timing.begin("Analyze class initializer");
    new StaticFieldValueAnalysis(appView.withLiveness(), code, feedback)
        .computeFieldOptimizationInfo(classInitializerDefaultsResult);
    timing.end();
  }

  @Override
  void computeFieldOptimizationInfo(ClassInitializerDefaultsResult classInitializerDefaultsResult) {
    super.computeFieldOptimizationInfo(classInitializerDefaultsResult);

    classInitializerDefaultsResult.forEachOptimizedField(
        (field, value) -> {
          if (putsPerField.containsKey(field)
              || !appView.appInfo().isFieldOnlyWrittenInMethod(field, context.getDefinition())) {
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
        && field.holder() == context.getHolderType()
        && appView.appInfo().isFieldOnlyWrittenInMethod(field, context.getDefinition());
  }

  @Override
  void updateFieldOptimizationInfo(DexEncodedField field, FieldInstruction fieldPut, Value value) {
    // Abstract value.
    Value root = value.getAliasedValue();
    AbstractValue abstractValue = root.getAbstractValue(appView, context);
    if (abstractValue.isUnknown()) {
      feedback.recordFieldHasAbstractValue(field, appView, computeSingleFieldValue(field, root));
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

  private SingleFieldValue computeSingleFieldValue(DexEncodedField field, Value value) {
    assert !value.hasAliasedValue();
    SingleFieldValue result = computeSingleEnumFieldValue(value);
    if (result != null) {
      return result;
    }
    return appView
        .abstractValueFactory()
        .createSingleFieldValue(field.field, computeObjectState(value));
  }

  /**
   * If {@param value} is defined by a new-instance instruction that instantiates the enclosing enum
   * class, and the value is assigned into exactly one static enum field on the enclosing enum
   * class, then returns a {@link SingleFieldValue} instance. Otherwise, returns {@code null}.
   *
   * <p>Note that enum constructors also store the newly instantiated enums in the {@code $VALUES}
   * array field on the enum. Therefore, this code also allows {@param value} to be stored into an
   * array as long as the array is identified as being the {@code $VALUES} array.
   */
  private SingleFieldValue computeSingleEnumFieldValue(Value value) {
    assert !value.hasAliasedValue();
    if (!context.getHolder().isEnum()
        || !value.isDefinedByInstructionSatisfying(Instruction::isNewInstance)) {
      return null;
    }

    NewInstance newInstance = value.definition.asNewInstance();
    if (newInstance.clazz != context.getHolderType()) {
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
          DexEncodedField field =
              context.getHolder().lookupStaticField(user.asStaticPut().getField());
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

    return appView
        .abstractValueFactory()
        .createSingleFieldValue(enumField.field, computeObjectState(value));
  }

  private ObjectState computeObjectState(Value value) {
    assert !value.hasAliasedValue();
    if (!value.isDefinedByInstructionSatisfying(Instruction::isNewInstance)) {
      return ObjectState.empty();
    }

    NewInstance newInstance = value.definition.asNewInstance();
    InvokeDirect uniqueConstructorInvoke =
        newInstance.getUniqueConstructorInvoke(appView.dexItemFactory());
    if (uniqueConstructorInvoke == null) {
      return ObjectState.empty();
    }

    DexEncodedMethod singleTarget = uniqueConstructorInvoke.lookupSingleTarget(appView, context);
    if (singleTarget == null) {
      return ObjectState.empty();
    }

    InstanceFieldInitializationInfoCollection initializationInfos =
        singleTarget.getOptimizationInfo().getInstanceInitializerInfo().fieldInitializationInfos();
    if (initializationInfos.isEmpty()) {
      return ObjectState.empty();
    }

    ObjectState.Builder builder = ObjectState.builder();
    initializationInfos.forEach(
        appView,
        (field, initializationInfo) -> {
          if (!appView.appInfo().isInstanceFieldWrittenOnlyInInstanceInitializers(field)) {
            return;
          }
          if (initializationInfo.isArgumentInitializationInfo()) {
            InstanceFieldArgumentInitializationInfo argumentInitializationInfo =
                initializationInfo.asArgumentInitializationInfo();
            Value argument =
                uniqueConstructorInvoke.getArgument(argumentInitializationInfo.getArgumentIndex());
            builder.recordFieldHasValue(field, argument.getAbstractValue(appView, context));
          } else if (initializationInfo.isSingleValue()) {
            builder.recordFieldHasValue(field, initializationInfo.asSingleValue());
          }
        });
    return builder.build();
  }

  private boolean isEnumValuesArray(Value value) {
    assert context.getHolder().isEnum();
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    DexField valuesField =
        dexItemFactory.createField(
            context.getHolderType(),
            context.getHolderType().toArrayType(1, dexItemFactory),
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
