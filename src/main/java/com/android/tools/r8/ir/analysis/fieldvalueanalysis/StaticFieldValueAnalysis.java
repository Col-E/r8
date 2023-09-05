// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldvalueanalysis;

import static com.android.tools.r8.ir.code.Opcodes.ARRAY_PUT;
import static com.android.tools.r8.ir.code.Opcodes.ASSUME;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_DIRECT;
import static com.android.tools.r8.ir.code.Opcodes.STATIC_PUT;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexValueNull;
import com.android.tools.r8.ir.analysis.type.DynamicTypeWithUpperBound;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.ir.analysis.value.NullOrAbstractValue;
import com.android.tools.r8.ir.analysis.value.SingleFieldValue;
import com.android.tools.r8.ir.analysis.value.UnknownValue;
import com.android.tools.r8.ir.analysis.value.objectstate.EnumValuesObjectState;
import com.android.tools.r8.ir.analysis.value.objectstate.ObjectState;
import com.android.tools.r8.ir.analysis.value.objectstate.ObjectStateAnalysis;
import com.android.tools.r8.ir.code.ArrayPut;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.NewArrayEmpty;
import com.android.tools.r8.ir.code.NewArrayFilled;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.ClassInitializerDefaultsOptimization.ClassInitializerDefaultsResult;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.Timing;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class StaticFieldValueAnalysis extends FieldValueAnalysis {

  private final StaticFieldValues.Builder builder;
  private final Map<Value, AbstractValue> computedValues = new IdentityHashMap<>();

  private StaticFieldValueAnalysis(
      AppView<AppInfoWithLiveness> appView, IRCode code, OptimizationFeedback feedback) {
    super(appView, code, feedback);
    builder = StaticFieldValues.builder(code.context().getHolder());
  }

  public static StaticFieldValues run(
      AppView<?> appView,
      IRCode code,
      ClassInitializerDefaultsResult classInitializerDefaultsResult,
      OptimizationFeedback feedback,
      Timing timing) {
    assert appView.appInfo().hasLiveness();
    assert appView.enableWholeProgramOptimizations();
    assert code.context().getDefinition().isClassInitializer();
    timing.begin("Analyze class initializer");
    StaticFieldValues result =
        new StaticFieldValueAnalysis(appView.withLiveness(), code, feedback)
            .analyze(classInitializerDefaultsResult);
    timing.end();
    return result;
  }

  @Override
  boolean isStaticFieldValueAnalysis() {
    return true;
  }

  @Override
  StaticFieldValueAnalysis asStaticFieldValueAnalysis() {
    return this;
  }

  StaticFieldValues analyze(ClassInitializerDefaultsResult classInitializerDefaultsResult) {
    computeFieldOptimizationInfo(classInitializerDefaultsResult);
    return builder.build();
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
        },
        appView);
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  boolean isSubjectToOptimization(DexClassAndField field) {
    return field.getAccessFlags().isStatic()
        && field.getHolderType() == context.getHolderType()
        && appView.appInfo().isFieldOnlyWrittenInMethod(field, context.getDefinition());
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  boolean isSubjectToOptimizationIgnoringPinning(DexClassAndField field) {
    return field.getAccessFlags().isStatic()
        && field.getHolderType() == context.getHolderType()
        && appView
            .appInfo()
            .isFieldOnlyWrittenInMethodIgnoringPinning(field, context.getDefinition());
  }

  @Override
  void updateFieldOptimizationInfo(DexClassAndField field, FieldInstruction fieldPut, Value value) {
    AbstractValue abstractValue = getOrComputeAbstractValue(value, field);
    updateFieldOptimizationInfo(field, value, abstractValue, false);
  }

  void updateFieldOptimizationInfo(
      DexClassAndField field, Value value, AbstractValue abstractValue, boolean maybeNull) {
    builder.recordStaticField(field, abstractValue, appView.dexItemFactory());

    // We cannot modify FieldOptimizationInfo of pinned fields.
    if (appView.appInfo().isPinned(field)) {
      return;
    }

    // Abstract value.
    feedback.recordFieldHasAbstractValue(field, appView, abstractValue);

    // Dynamic type.
    if (field.getType().isReferenceType()) {
      DynamicTypeWithUpperBound staticType = field.getType().toDynamicType(appView);
      DynamicTypeWithUpperBound dynamicType = value.getDynamicType(appView);
      if (dynamicType.strictlyLessThan(staticType, appView)) {
        if (maybeNull && dynamicType.getNullability().isDefinitelyNotNull()) {
          assert dynamicType.getDynamicUpperBoundType().isReferenceType();
          dynamicType = dynamicType.withNullability(Nullability.maybeNull());
        }
        feedback.markFieldHasDynamicType(field, dynamicType);
      }
    }
  }

  @SuppressWarnings("ReferenceEquality")
  public void updateFieldOptimizationInfoWith2Values(
      DexClassAndField field, Value valuePut, DexValue valueBeforePut) {
    // We are interested in the AbstractValue only if it's null or a value, so we can use the value
    // if the code is protected by a null check.
    if (valueBeforePut != DexValueNull.NULL) {
      return;
    }

    AbstractValue abstractValue =
        NullOrAbstractValue.create(getOrComputeAbstractValue(valuePut, field));
    updateFieldOptimizationInfo(field, valuePut, abstractValue, true);
  }

  private AbstractValue getOrComputeAbstractValue(Value value, DexClassAndField field) {
    Value root = value.getAliasedValue();
    AbstractValue abstractValue = root.getAbstractValue(appView, context);
    if (!abstractValue.isSingleValue()) {
      return computeSingleFieldValue(field, root);
    }
    return abstractValue;
  }

  private SingleFieldValue computeSingleFieldValue(DexClassAndField field, Value value) {
    assert !value.hasAliasedValue();
    SingleFieldValue result = computeSingleEnumFieldValue(value);
    if (result != null) {
      return result;
    }
    return appView
        .abstractValueFactory()
        .createSingleFieldValue(field.getReference(), computeObjectState(value));
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
    if (!context.getHolder().isEnum()) {
      return null;
    }
    assert !value.hasAliasedValue();
    if (value.isPhi()) {
      return null;
    }
    if (value.definition.isNewArrayEmptyOrNewArrayFilled()) {
      return computeSingleEnumFieldValueForValuesArray(value);
    }
    if (value.definition.isNewInstance()) {
      return computeSingleEnumFieldValueForInstance(value);
    }
    return null;
  }

  private SingleFieldValue computeSingleEnumFieldValueForValuesArray(Value value) {
    if (!value.definition.isNewArrayEmptyOrNewArrayFilled()) {
      return null;
    }
    AbstractValue valuesValue = computedValues.get(value);
    if (valuesValue != null) {
      // This implicitely answers null if the value could not get computed.
      if (valuesValue.isSingleFieldValue()) {
        SingleFieldValue fieldValue = valuesValue.asSingleFieldValue();
        if (fieldValue.getObjectState().isEnumValuesObjectState()) {
          return fieldValue;
        }
      }
      return null;
    }
    SingleFieldValue singleFieldValue = internalComputeSingleEnumFieldValueForValuesArray(value);
    computedValues.put(
        value, singleFieldValue == null ? UnknownValue.getInstance() : singleFieldValue);
    return singleFieldValue;
  }

  @SuppressWarnings("ReferenceEquality")
  private SingleFieldValue internalComputeSingleEnumFieldValueForValuesArray(Value value) {
    NewArrayEmpty newArrayEmpty = value.definition.asNewArrayEmpty();
    NewArrayFilled newArrayFilled = value.definition.asNewArrayFilled();
    assert newArrayEmpty != null || newArrayFilled != null;

    DexType arrayType = newArrayEmpty != null ? newArrayEmpty.type : newArrayFilled.getArrayType();
    if (arrayType.toBaseType(appView.dexItemFactory()) != context.getHolder().type) {
      return null;
    }
    if (value.hasDebugUsers() || value.hasPhiUsers()) {
      return null;
    }

    int valuesSize = newArrayEmpty != null ? newArrayEmpty.sizeIfConst() : newArrayFilled.size();
    if (valuesSize < 1) {
      // Array is empty or non-const size.
      return null;
    }

    DexType[] valuesTypes = new DexType[valuesSize];
    ObjectState[] valuesState = new ObjectState[valuesSize];

    if (newArrayFilled != null) {
      // Populate array values from filled-new-array values.
      List<Value> inValues = newArrayFilled.inValues();
      for (int i = 0; i < valuesSize; ++i) {
        if (!updateEnumValueState(valuesState, valuesTypes, i, inValues.get(i))) {
          return null;
        }
      }
    }

    // Populate / update array values from aput-object instructions, and find the static-put
    // instruction.
    DexEncodedField valuesField = null;
    for (Instruction user : value.aliasedUsers()) {
      switch (user.opcode()) {
        case ARRAY_PUT:
          ArrayPut arrayPut = user.asArrayPut();
          if (arrayPut.array() != value) {
            return null;
          }
          if (!arrayPut.index().isConstNumber()) {
            return null;
          }
          int index = arrayPut.index().getConstInstruction().asConstNumber().getIntValue();
          if (index < 0 || index >= valuesSize) {
            return null;
          }
          if (!updateEnumValueState(valuesState, valuesTypes, index, arrayPut.value())) {
            return null;
          }
          break;

        case ASSUME:
          if (user.outValue().hasPhiUsers()) {
            return null;
          }
          break;

        case STATIC_PUT:
          DexEncodedField field =
              context.getHolder().lookupStaticField(user.asStaticPut().getField());
          if (field == null) {
            return null;
          }
          if (valuesField != null) {
            return null;
          }
          valuesField = field;
          break;

        default:
          return null;
      }
    }

    if (valuesField == null) {
      return null;
    }

    if (ArrayUtils.contains(valuesState, null)) {
      return null;
    }
    // This should be guaranteed since valuesState and valuesTypes are updated at the same time.
    assert !ArrayUtils.contains(valuesTypes, null);

    return appView
        .abstractValueFactory()
        .createSingleFieldValue(
            valuesField.getReference(), new EnumValuesObjectState(valuesState, valuesTypes));
  }

  private boolean updateEnumValueState(
      ObjectState[] valuesState, DexType[] valuesTypes, int index, Value value) {
    Value root = value.getAliasedValue();
    if (root.isPhi()) {
      return false;
    }
    Instruction definition = root.getDefinition();
    if (definition.isStaticGet()) {
      // Enums with many instance rely on staticGets to set the $VALUES data instead of directly
      // keeping the values in registers, due to the max capacity of the redundant field load
      // elimination. The capacity has already been increased, so that this case is extremely
      // uncommon (very large enums).
      // TODO(b/169050248): We could consider analysing these to answer the object state here.
      return false;
    }
    ObjectState objectState =
        definition.isNewInstance() ? computeObjectState(definition.outValue()) : null;
    if (objectState == null || objectState.isEmpty()) {
      // We need the state of all fields for the analysis to be valuable.
      return false;
    }
    if (!valuesArrayIndexMatchesOrdinal(index, objectState)) {
      return false;
    }
    if (valuesState[index] != null) {
      return false;
    }
    assert definition.isNewInstance();
    valuesTypes[index] = definition.asNewInstance().getType();
    valuesState[index] = objectState;
    return true;
  }

  private boolean valuesArrayIndexMatchesOrdinal(int ordinal, ObjectState objectState) {
    DexEncodedField ordinalField =
        appView
            .appInfo()
            .resolveField(appView.dexItemFactory().enumMembers.ordinalField, context)
            .getResolvedField();
    if (ordinalField == null) {
      return false;
    }
    AbstractValue ordinalState = objectState.getAbstractFieldValue(ordinalField);
    if (ordinalState == null || !ordinalState.isSingleNumberValue()) {
      return false;
    }
    int intValue = ordinalState.asSingleNumberValue().getIntValue();
    return intValue == ordinal;
  }

  @SuppressWarnings("ReferenceEquality")
  private SingleFieldValue computeSingleEnumFieldValueForInstance(Value value) {
    assert value.isDefinedByInstructionSatisfying(Instruction::isNewInstance);

    NewInstance newInstance = value.definition.asNewInstance();
    // Some enums have direct subclasses, and the subclass is instantiated here.
    if (newInstance.clazz != context.getHolderType()) {
      DexClass dexClass = appView.definitionFor(newInstance.clazz);
      if (dexClass == null || dexClass.superType != context.getHolderType()) {
        return null;
      }
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
        .createSingleFieldValue(enumField.getReference(), computeObjectState(value));
  }

  private ObjectState computeObjectState(Value value) {
    // TODO(b/204159267): Move this logic into Instruction#getAbstractValue in NewInstance.
    return ObjectStateAnalysis.computeObjectState(value, appView, context);
  }

  private boolean isEnumValuesArray(Value value) {
    SingleFieldValue singleFieldValue = computeSingleEnumFieldValueForValuesArray(value);
    return singleFieldValue != null && singleFieldValue.getObjectState().isEnumValuesObjectState();
  }
}
