// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value;

import static com.android.tools.r8.utils.BitUtils.ALL_BITS_SET_MASK;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.objectstate.KnownLengthArrayState;
import com.android.tools.r8.ir.analysis.value.objectstate.ObjectState;
import com.android.tools.r8.naming.dexitembasedstring.NameComputationInfo;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.TestingOptions;
import java.util.concurrent.ConcurrentHashMap;

public class AbstractValueFactory {

  private final TestingOptions testingOptions;

  private final ConcurrentHashMap<DexType, SingleConstClassValue> singleConstClassValues =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Long, SingleNumberValue> singleNumberValues =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<DexString, SingleStringValue> singleStringValues =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Integer, KnownLengthArrayState> knownArrayLengthStates =
      new ConcurrentHashMap<>();

  public AbstractValueFactory(InternalOptions options) {
    testingOptions = options.testing;
  }

  public SingleBoxedBooleanValue createBoxedBooleanFalse() {
    return SingleBoxedBooleanValue.getFalseInstance();
  }

  public SingleBoxedBooleanValue createBoxedBooleanTrue() {
    return SingleBoxedBooleanValue.getTrueInstance();
  }

  public SingleBoxedByteValue createBoxedByte(int value) {
    return new SingleBoxedByteValue(value);
  }

  public SingleBoxedCharValue createBoxedChar(int value) {
    return new SingleBoxedCharValue(value);
  }

  public SingleBoxedDoubleValue createBoxedDouble(long value) {
    return new SingleBoxedDoubleValue(value);
  }

  public SingleBoxedFloatValue createBoxedFloat(int value) {
    return new SingleBoxedFloatValue(value);
  }

  public SingleBoxedIntegerValue createBoxedInteger(int value) {
    return new SingleBoxedIntegerValue(value);
  }

  public SingleBoxedLongValue createBoxedLong(long value) {
    return new SingleBoxedLongValue(value);
  }

  public SingleBoxedShortValue createBoxedShort(int value) {
    return new SingleBoxedShortValue(value);
  }

  public SingleConstValue createDefaultValue(DexType type) {
    assert type.isPrimitiveType() || type.isReferenceType();
    return type.isPrimitiveType() ? createZeroValue() : createNullValue(type);
  }

  public AbstractValue createDefiniteBitsNumberValue(
      int definitelySetBits, int definitelyUnsetBits) {
    if (definitelySetBits != 0 || definitelyUnsetBits != 0) {
      // If all bits are known, then create a single number value.
      boolean allBitsSet = (definitelySetBits | definitelyUnsetBits) == ALL_BITS_SET_MASK;
      // Account for the temporary hack in the Compose modeling where we create a
      // DefiniteBitsNumberValue with set bits=0b1^32 and unset bits = 0b1^(31)0. This value is used
      // to simulate the effect of `x | 1` in joins.
      if (testingOptions.modelUnknownChangedAndDefaultArgumentsToComposableFunctions) {
        boolean overlappingSetAndUnsetBits = (definitelySetBits & definitelyUnsetBits) != 0;
        if (overlappingSetAndUnsetBits) {
          allBitsSet = false;
        }
      }
      if (allBitsSet) {
        return createUncheckedSingleNumberValue(definitelySetBits);
      }
      return new DefiniteBitsNumberValue(definitelySetBits, definitelyUnsetBits, testingOptions);
    }
    return AbstractValue.unknown();
  }

  public SingleConstClassValue createSingleConstClassValue(DexType type) {
    return singleConstClassValues.computeIfAbsent(type, SingleConstClassValue::new);
  }

  public KnownLengthArrayState createKnownLengthArrayState(int length) {
    return knownArrayLengthStates.computeIfAbsent(length, KnownLengthArrayState::new);
  }

  public NumberFromIntervalValue createNumberFromIntervalValue(
      long minInclusive, long maxInclusive) {
    return new NumberFromIntervalValue(minInclusive, maxInclusive);
  }

  public SingleFieldValue createSingleFieldValue(DexField field, ObjectState state) {
    return state.isEmpty()
        ? new SingleStatelessFieldValue(field)
        : new SingleStatefulFieldValue(field, state);
  }

  public SingleNumberValue createSingleNumberValue(long value, TypeElement type) {
    assert type.isPrimitiveType();
    return createUncheckedSingleNumberValue(value);
  }

  public SingleNumberValue createUncheckedSingleNumberValue(long value) {
    return singleNumberValues.computeIfAbsent(value, SingleNumberValue::new);
  }

  public SingleNullValue createNullValue(DexType type) {
    assert type.isReferenceType() : type;
    return createUncheckedNullValue();
  }

  public SingleNullValue createNullValue(TypeElement type) {
    assert type.isReferenceType();
    return createUncheckedNullValue();
  }

  public SingleNullValue createUncheckedNullValue() {
    return SingleNullValue.get();
  }

  public SingleNumberValue createZeroValue() {
    return createUncheckedSingleNumberValue(0);
  }

  public SingleStringValue createSingleStringValue(DexString string) {
    return singleStringValues.computeIfAbsent(string, SingleStringValue::new);
  }

  public SingleDexItemBasedStringValue createSingleDexItemBasedStringValue(
      DexReference reference, NameComputationInfo<?> nameComputationInfo) {
    return new SingleDexItemBasedStringValue(reference, nameComputationInfo);
  }
}
