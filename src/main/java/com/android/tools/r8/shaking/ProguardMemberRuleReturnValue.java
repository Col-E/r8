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
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.ir.analysis.value.objectstate.ObjectState;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.LongInterval;

public class ProguardMemberRuleReturnValue {
  public enum Type {
    BOOLEAN,
    VALUE_RANGE,
    FIELD,
    NULL
  }

  private final Type type;
  private final boolean booleanValue;
  private final LongInterval longInterval;
  private final DexType fieldHolder;
  private final DexString fieldName;
  private final boolean notNull;

  ProguardMemberRuleReturnValue(boolean value) {
    this.type = Type.BOOLEAN;
    this.booleanValue = value;
    this.longInterval = null;
    this.fieldHolder = null;
    this.fieldName = null;
    this.notNull = false;
  }

  ProguardMemberRuleReturnValue(LongInterval value) {
    this.type = Type.VALUE_RANGE;
    this.booleanValue = false;
    this.longInterval = value;
    this.fieldHolder = null;
    this.fieldName = null;
    this.notNull = false;
  }

  ProguardMemberRuleReturnValue(DexType fieldHolder, DexString fieldName) {
    this.type = Type.FIELD;
    this.booleanValue = false;
    this.longInterval = null;
    this.fieldHolder = fieldHolder;
    this.fieldName = fieldName;
    this.notNull = false;
  }

  ProguardMemberRuleReturnValue() {
    this.type = Type.NULL;
    this.booleanValue = false;
    this.longInterval = null;
    this.fieldHolder = null;
    this.fieldName = null;
    this.notNull = false;
  }

  public boolean isBoolean() {
    return type == Type.BOOLEAN;
  }

  public boolean isValueRange() {
    return type == Type.VALUE_RANGE;
  }

  public boolean isField() {
    return type == Type.FIELD;
  }

  public boolean isNonNull() {
    return isValueRange() && getValueRange().getMin() > 0;
  }

  public boolean isNull() {
    return type == Type.NULL;
  }

  public boolean getBoolean() {
    assert isBoolean();
    return booleanValue;
  }

  /**
   * Returns if this return value is a single value.
   *
   * Boolean values and null are considered a single value.
   */
  public boolean isSingleValue() {
    return isBoolean() || isNull() || (isValueRange() && longInterval.isSingleValue());
  }

  /**
   * Returns the return value.
   *
   * Boolean values are returned as 0 for <code>false</code> and 1 for <code>true</code>.
   *
   * Reference value <code>null</code> is returned as 0.
   */
  public long getSingleValue() {
    assert isSingleValue();
    if (isBoolean()) {
      return booleanValue ? 1 : 0;
    }
    if (isNull()) {
      return 0;
    }
    return longInterval.getSingleValue();
  }

  public LongInterval getValueRange() {
    assert isValueRange();
    return longInterval;
  }

  public DexType getFieldHolder() {
    assert isField();
    return fieldHolder;
  }

  public DexString getFieldName() {
    assert isField();
    return fieldName;
  }

  public AbstractValue toAbstractValue(AppView<?> appView, DexType valueType) {
    AbstractValueFactory abstractValueFactory = appView.abstractValueFactory();
    switch (type) {
      case BOOLEAN:
        return abstractValueFactory.createSingleNumberValue(BooleanUtils.intValue(booleanValue));
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
      case NULL:
        return abstractValueFactory.createNullValue();
      case VALUE_RANGE:
        if (longInterval.isSingleValue()) {
          long singleValue = longInterval.getSingleValue();
          if (valueType.isReferenceType()) {
            return singleValue == 0
                ? abstractValueFactory.createNullValue()
                : AbstractValue.unknown();
          }
          return abstractValueFactory.createSingleNumberValue(singleValue);
        }
        return abstractValueFactory.createNumberFromIntervalValue(
            longInterval.getMin(), longInterval.getMax());
      default:
        throw new Unreachable("Unexpected type: " + type);
    }
  }

  public DynamicType toDynamicType(AppView<?> appView, DexType valueType) {
    if (valueType.isReferenceType()) {
      if (type == Type.VALUE_RANGE && !longInterval.containsValue(0)) {
        return DynamicType.definitelyNotNull();
      }
      if (notNull) {
        return DynamicType.definitelyNotNull();
      }
    }
    return DynamicType.unknown();
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();
    result.append(" return ");
    if (isBoolean()) {
      result.append(booleanValue ? "true" : "false");
    } else if (isNull()) {
      result.append("null");
    } else if (isValueRange()) {
      result.append(longInterval.getMin());
      if (!isSingleValue()) {
        result.append("..");
        result.append(longInterval.getMax());
      }
    } else {
      assert isField();
      result.append(getFieldHolder().getTypeName()).append('.').append(getFieldName());
    }
    return result.toString();
  }
}
