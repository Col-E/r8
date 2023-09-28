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
import java.util.concurrent.ConcurrentHashMap;

public class AbstractValueFactory {

  private ConcurrentHashMap<DexType, SingleConstClassValue> singleConstClassValues =
      new ConcurrentHashMap<>();
  private ConcurrentHashMap<Long, SingleNumberValue> singleNumberValues = new ConcurrentHashMap<>();
  private ConcurrentHashMap<DexString, SingleStringValue> singleStringValues =
      new ConcurrentHashMap<>();
  private ConcurrentHashMap<Integer, KnownLengthArrayState> knownArrayLengthStates =
      new ConcurrentHashMap<>();

  public SingleConstValue createDefaultValue(DexType type) {
    assert type.isPrimitiveType() || type.isReferenceType();
    return type.isPrimitiveType() ? createZeroValue() : createNullValue(type);
  }

  public AbstractValue createDefiniteBitsNumberValue(
      int definitelySetBits, int definitelyUnsetBits) {
    if (definitelySetBits != 0 || definitelyUnsetBits != 0) {
      // If all bits are known, then create a single number value.
      if ((definitelySetBits | definitelyUnsetBits) == ALL_BITS_SET_MASK) {
        return createUncheckedSingleNumberValue(definitelySetBits);
      }
      return new DefiniteBitsNumberValue(definitelySetBits, definitelyUnsetBits);
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
