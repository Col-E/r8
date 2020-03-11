// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.inspector.internal;

import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.inspector.BooleanValueInspector;
import com.android.tools.r8.inspector.ByteValueInspector;
import com.android.tools.r8.inspector.CharValueInspector;
import com.android.tools.r8.inspector.DoubleValueInspector;
import com.android.tools.r8.inspector.FloatValueInspector;
import com.android.tools.r8.inspector.IntValueInspector;
import com.android.tools.r8.inspector.LongValueInspector;
import com.android.tools.r8.inspector.ShortValueInspector;
import com.android.tools.r8.inspector.StringValueInspector;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;

public class ValueInspectorImpl
    implements BooleanValueInspector,
        ByteValueInspector,
        CharValueInspector,
        ShortValueInspector,
        IntValueInspector,
        LongValueInspector,
        FloatValueInspector,
        DoubleValueInspector,
        StringValueInspector {

  private final DexValue value;
  private final DexType type;

  public ValueInspectorImpl(DexValue value, DexType type) {
    this.value = value;
    this.type = type;
  }

  @Override
  public TypeReference getTypeReference() {
    return Reference.typeFromDescriptor(type.toDescriptorString());
  }

  @Override
  public boolean isPrimitive() {
    return type.isPrimitiveType();
  }

  @Override
  public boolean isBooleanValue() {
    return type.isBooleanType();
  }

  @Override
  public BooleanValueInspector asBooleanValue() {
    return isBooleanValue() ? this : null;
  }

  @Override
  public boolean getBooleanValue() {
    guard(isBooleanValue());
    return value.asDexValueBoolean().getValue();
  }

  @Override
  public boolean isByteValue() {
    return type.isByteType();
  }

  @Override
  public ByteValueInspector asByteValue() {
    return isByteValue() ? this : null;
  }

  @Override
  public byte getByteValue() {
    guard(isByteValue());
    return value.asDexValueByte().getValue();
  }

  @Override
  public boolean isCharValue() {
    return type.isCharType();
  }

  @Override
  public CharValueInspector asCharValue() {
    return isCharValue() ? this : null;
  }

  @Override
  public char getCharValue() {
    guard(isCharValue());
    return value.asDexValueChar().getValue();
  }

  @Override
  public boolean isShortValue() {
    return type.isShortType();
  }

  @Override
  public ShortValueInspector asShortValue() {
    return isShortValue() ? this : null;
  }

  @Override
  public short getShortValue() {
    guard(isShortValue());
    return value.asDexValueShort().getValue();
  }

  @Override
  public boolean isIntValue() {
    return type.isIntType();
  }

  @Override
  public IntValueInspector asIntValue() {
    return isIntValue() ? this : null;
  }

  @Override
  public int getIntValue() {
    guard(isIntValue());
    return value.asDexValueInt().value;
  }

  @Override
  public boolean isLongValue() {
    return type.isLongType();
  }

  @Override
  public LongValueInspector asLongValue() {
    return isLongValue() ? this : null;
  }

  @Override
  public long getLongValue() {
    guard(isLongValue());
    return value.asDexValueLong().getValue();
  }

  @Override
  public boolean isFloatValue() {
    return type.isFloatType();
  }

  @Override
  public FloatValueInspector asFloatValue() {
    return isFloatValue() ? this : null;
  }

  @Override
  public float getFloatValue() {
    guard(isFloatValue());
    return value.asDexValueFloat().getValue();
  }

  @Override
  public boolean isDoubleValue() {
    return type.isDoubleType();
  }

  @Override
  public DoubleValueInspector asDoubleValue() {
    return isDoubleValue() ? this : null;
  }

  @Override
  public double getDoubleValue() {
    guard(isDoubleValue());
    return value.asDexValueDouble().getValue();
  }

  @Override
  public boolean isStringValue() {
    return type.isClassType() && value.isDexValueString();
  }

  @Override
  public StringValueInspector asStringValue() {
    return isStringValue() ? this : null;
  }

  @Override
  public String getStringValue() {
    guard(isStringValue());
    return value.asDexValueString().getValue().toString();
  }

  private static void guard(boolean precondition) {
    if (!precondition) {
      throw new IllegalStateException("Invalid call on ValueInspector");
    }
  }
}
