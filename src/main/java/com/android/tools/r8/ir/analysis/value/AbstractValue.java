// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.analysis.value.objectstate.ObjectState;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public abstract class AbstractValue {

  public static BottomValue bottom() {
    return BottomValue.getInstance();
  }

  public static UnknownValue unknown() {
    return UnknownValue.getInstance();
  }

  public abstract boolean isNonTrivial();

  public boolean isSingleBoolean() {
    return false;
  }

  public boolean isBottom() {
    return false;
  }

  public boolean isFalse() {
    return false;
  }

  public boolean isTrue() {
    return false;
  }

  public boolean isNull() {
    return false;
  }

  public final boolean isZero() {
    return isFalse();
  }

  /**
   * Returns true if this abstract value represents a single concrete value (i.e., the
   * concretization of this abstract value has size 1).
   */
  public boolean isSingleValue() {
    return false;
  }

  public SingleValue asSingleValue() {
    return null;
  }

  public boolean hasObjectState() {
    return false;
  }

  public ObjectState getObjectState() {
    throw new UnsupportedOperationException(
        "Abstract value " + this + " does not have any object state.");
  }

  public boolean isStatefulObjectValue() {
    return false;
  }

  public StatefulObjectValue asStatefulObjectValue() {
    return null;
  }

  public boolean hasDefinitelySetAndUnsetBitsInformation() {
    return false;
  }

  public int getDefinitelySetIntBits() {
    throw new Unreachable();
  }

  public int getDefinitelyUnsetIntBits() {
    throw new Unreachable();
  }

  public boolean hasKnownArrayLength() {
    return false;
  }

  public int getKnownArrayLength() {
    throw new UnsupportedOperationException(
        "Abstract value " + this + " does not have a known array length.");
  }

  public boolean isSingleBoxedPrimitive() {
    return false;
  }

  public SingleBoxedPrimitiveValue asSingleBoxedPrimitive() {
    return null;
  }

  public boolean isSingleBoxedBoolean() {
    return false;
  }

  public SingleBoxedBooleanValue asSingleBoxedBoolean() {
    return null;
  }

  public boolean isSingleBoxedByte() {
    return false;
  }

  public SingleBoxedByteValue asSingleBoxedByte() {
    return null;
  }

  public boolean isSingleBoxedChar() {
    return false;
  }

  public SingleBoxedCharValue asSingleBoxedChar() {
    return null;
  }

  public boolean isSingleBoxedDouble() {
    return false;
  }

  public SingleBoxedDoubleValue asSingleBoxedDouble() {
    return null;
  }

  public boolean isSingleBoxedFloat() {
    return false;
  }

  public SingleBoxedFloatValue asSingleBoxedFloat() {
    return null;
  }

  public boolean isSingleBoxedLong() {
    return false;
  }

  public SingleBoxedLongValue asSingleBoxedLong() {
    return null;
  }

  public boolean isSingleBoxedInteger() {
    return false;
  }

  public SingleBoxedIntegerValue asSingleBoxedInteger() {
    return null;
  }

  public boolean isSingleBoxedShort() {
    return false;
  }

  public SingleBoxedShortValue asSingleBoxedShort() {
    return null;
  }

  public boolean isSingleConstValue() {
    return false;
  }

  public SingleConstValue asSingleConstValue() {
    return null;
  }

  public boolean isSingleConstClassValue() {
    return false;
  }

  public SingleConstClassValue asSingleConstClassValue() {
    return null;
  }

  public boolean isSingleFieldValue() {
    return false;
  }

  public SingleFieldValue asSingleFieldValue() {
    return null;
  }

  public SingleNullValue asSingleNullValue() {
    return null;
  }

  public boolean isSingleNumberValue() {
    return false;
  }

  public SingleNumberValue asSingleNumberValue() {
    return null;
  }

  public boolean isSingleStringValue() {
    return false;
  }

  public SingleStringValue asSingleStringValue() {
    return null;
  }

  public boolean isSingleDexItemBasedStringValue() {
    return false;
  }

  public SingleDexItemBasedStringValue asSingleDexItemBasedStringValue() {
    return null;
  }

  public boolean isUnknown() {
    return false;
  }

  public boolean isNullOrAbstractValue() {
    return false;
  }

  public NullOrAbstractValue asNullOrAbstractValue() {
    return null;
  }

  public boolean isConstantOrNonConstantNumberValue() {
    return false;
  }

  public ConstantOrNonConstantNumberValue asConstantOrNonConstantNumberValue() {
    return null;
  }

  public boolean isNonConstantNumberValue() {
    return false;
  }

  public NonConstantNumberValue asNonConstantNumberValue() {
    return null;
  }

  public boolean isNumberFromIntervalValue() {
    return false;
  }

  public NumberFromIntervalValue asNumberFromIntervalValue() {
    return null;
  }

  public boolean isNumberFromSetValue() {
    return false;
  }

  public NumberFromSetValue asNumberFromSetValue() {
    return null;
  }

  public boolean isDefiniteBitsNumberValue() {
    return false;
  }

  public DefiniteBitsNumberValue asDefiniteBitsNumberValue() {
    return null;
  }

  public abstract AbstractValue rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, DexType newType, GraphLens lens, GraphLens codeLens);

  @Override
  public abstract boolean equals(Object o);

  @Override
  public abstract int hashCode();

  @Override
  public abstract String toString();
}
