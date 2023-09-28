// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.ir.analysis.value.objectstate.ObjectState;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.LongInterval;

public class ProguardMemberRuleReturnValue {

  private enum Type {
    BOOLEAN,
    FIELD,
    NULLABILITY,
    VALUE_RANGE
  }

  private final Type type;
  private final boolean booleanValue;
  private final LongInterval longInterval;
  private final DexType fieldHolder;
  private final DexString fieldName;
  private final Nullability nullability;

  ProguardMemberRuleReturnValue(boolean value) {
    this.type = Type.BOOLEAN;
    this.booleanValue = value;
    this.longInterval = null;
    this.fieldHolder = null;
    this.fieldName = null;
    this.nullability = null;
  }

  @SuppressWarnings("InconsistentOverloads")
  ProguardMemberRuleReturnValue(DexType fieldHolder, DexString fieldName, Nullability nullability) {
    assert !nullability.isDefinitelyNull();
    this.type = Type.FIELD;
    this.booleanValue = false;
    this.longInterval = null;
    this.fieldHolder = fieldHolder;
    this.fieldName = fieldName;
    this.nullability = nullability;
  }

  ProguardMemberRuleReturnValue(Nullability nullability) {
    assert nullability.isDefinitelyNull() || nullability.isDefinitelyNotNull();
    this.type = Type.NULLABILITY;
    this.booleanValue = false;
    this.longInterval = null;
    this.fieldHolder = null;
    this.fieldName = null;
    this.nullability = nullability;
  }

  ProguardMemberRuleReturnValue(LongInterval value) {
    this.type = Type.VALUE_RANGE;
    this.booleanValue = false;
    this.longInterval = value;
    this.fieldHolder = null;
    this.fieldName = null;
    this.nullability = getNullabilityForValueRange(value);
  }

  private static Nullability getNullabilityForValueRange(LongInterval value) {
    if (value.isSingleValue(0)) {
      return Nullability.definitelyNull();
    } else if (!value.containsValue(0)) {
      return Nullability.definitelyNotNull();
    } else {
      return Nullability.maybeNull();
    }
  }

  public boolean isBoolean() {
    return type == Type.BOOLEAN;
  }

  public boolean isField() {
    return type == Type.FIELD;
  }

  public boolean isNullability() {
    return type == Type.NULLABILITY;
  }

  public boolean isValueRange() {
    return type == Type.VALUE_RANGE;
  }

  public boolean getBoolean() {
    assert isBoolean();
    return booleanValue;
  }

  public DexType getFieldHolder() {
    assert isField();
    return fieldHolder;
  }

  public DexString getFieldName() {
    assert isField();
    return fieldName;
  }

  private boolean hasNullability() {
    return isField() || isNullability() || isValueRange();
  }

  public Nullability getNullability() {
    assert hasNullability();
    return nullability;
  }

  public LongInterval getValueRange() {
    assert isValueRange();
    return longInterval;
  }

  public AbstractValue toAbstractValue(AppView<?> appView, DexType valueType) {
    AbstractValueFactory abstractValueFactory = appView.abstractValueFactory();
    switch (type) {
      case BOOLEAN:
        return abstractValueFactory.createSingleNumberValue(
            BooleanUtils.intValue(booleanValue), TypeElement.getBoolean());

      case FIELD:
        DexClass holder = appView.definitionFor(fieldHolder);
        if (holder != null) {
          DexEncodedField field = holder.lookupUniqueStaticFieldWithName(fieldName);
          if (field != null) {
            return abstractValueFactory.createSingleFieldValue(
                field.getReference(), ObjectState.empty());
          }
        }
        return AbstractValue.unknown();

      case NULLABILITY:
        return nullability.isDefinitelyNull()
            ? abstractValueFactory.createUncheckedNullValue()
            : AbstractValue.unknown();

      case VALUE_RANGE:
        if (valueType.isReferenceType()) {
          return nullability.isDefinitelyNull()
              ? abstractValueFactory.createUncheckedNullValue()
              : AbstractValue.unknown();
        }
        return longInterval.isSingleValue()
            ? abstractValueFactory.createSingleNumberValue(
                longInterval.getSingleValue(), TypeElement.getLong())
            : abstractValueFactory.createNumberFromIntervalValue(
                longInterval.getMin(), longInterval.getMax());

      default:
        throw new Unreachable("Unexpected type: " + type);
    }
  }

  public DynamicType toDynamicType(AppView<?> appView, DexType valueType) {
    return valueType.isReferenceType() && hasNullability() && getNullability().isDefinitelyNotNull()
        ? DynamicType.definitelyNotNull()
        : DynamicType.unknown();
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();
    result.append(" return ");
    if (isBoolean()) {
      result.append(booleanValue);
    } else if (isField()) {
      if (nullability.isDefinitelyNotNull()) {
        result.append("_NONNULL_ ");
      }
      result.append(fieldHolder.getTypeName()).append('.').append(fieldName);
    } else if (isNullability()) {
      result.append(nullability.isDefinitelyNull() ? "null" : "_NONNULL_");
    } else {
      assert isValueRange();
      result.append(longInterval.getMin());
      if (!longInterval.isSingleValue()) {
        result.append("..");
        result.append(longInterval.getMax());
      }
    }
    return result.toString();
  }
}
