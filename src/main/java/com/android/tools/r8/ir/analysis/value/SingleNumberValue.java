// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.TypeAndLocalInfoSupplier;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.OptionalBool;

public class SingleNumberValue extends SingleConstValue
    implements ConstantOrNonConstantNumberValue {

  private final long value;

  /** Intentionally package private, use {@link AbstractValueFactory} instead. */
  SingleNumberValue(long value) {
    this.value = value;
  }

  @Override
  public boolean containsInt(int value) {
    return value == getIntValue();
  }

  @Override
  public boolean hasDefinitelySetAndUnsetBitsInformation() {
    return true;
  }

  @Override
  public OptionalBool isSubsetOf(int[] values) {
    return OptionalBool.of(ArrayUtils.containsInt(values, getIntValue()));
  }

  @Override
  public boolean isSingleBoolean() {
    return isFalse() || isTrue();
  }

  @Override
  public boolean isFalse() {
    return value == 0;
  }

  @Override
  public boolean isTrue() {
    return value == 1;
  }

  @Override
  public boolean isSingleNumberValue() {
    return true;
  }

  @Override
  public SingleNumberValue asSingleNumberValue() {
    return this;
  }

  @Override
  public boolean isConstantOrNonConstantNumberValue() {
    return true;
  }

  @Override
  public ConstantOrNonConstantNumberValue asConstantOrNonConstantNumberValue() {
    return this;
  }

  public boolean getBooleanValue() {
    assert value == 0 || value == 1;
    return value != 0;
  }

  public int getDefinitelySetIntBits() {
    return getIntValue();
  }

  public int getDefinitelyUnsetIntBits() {
    return ~getDefinitelySetIntBits();
  }

  public double getDoubleValue() {
    return Double.longBitsToDouble(value);
  }

  public float getFloatValue() {
    return Float.intBitsToFloat((int) value);
  }

  public int getIntValue() {
    return (int) value;
  }

  public long getLongValue() {
    return value;
  }

  public long getValue() {
    return value;
  }

  @Override
  public boolean mayOverlapWith(ConstantOrNonConstantNumberValue other) {
    if (other.isSingleNumberValue()) {
      return equals(other.asSingleNumberValue());
    }
    assert other.isNonConstantNumberValue();
    return other.asNonConstantNumberValue().containsInt(getIntValue());
  }

  @Override
  public boolean equals(Object o) {
    return this == o;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  @Override
  public String toString() {
    return "SingleNumberValue(" + value + ")";
  }

  @Override
  public Instruction createMaterializingInstruction(
      AppView<?> appView,
      ProgramMethod context,
      NumberGenerator valueNumberGenerator,
      TypeAndLocalInfoSupplier info) {
    TypeElement typeLattice = info.getOutType();
    DebugLocalInfo debugLocalInfo = info.getLocalInfo();
    assert !typeLattice.isReferenceType() || value == 0;
    Value returnedValue =
        new Value(
            valueNumberGenerator.next(),
            typeLattice.isReferenceType() ? TypeElement.getNull() : typeLattice,
            debugLocalInfo);
    return new ConstNumber(returnedValue, value);
  }

  @Override
  boolean internalIsMaterializableInContext(
      AppView<? extends AppInfoWithClassHierarchy> appView, ProgramMethod context) {
    return true;
  }

  @Override
  public boolean isMaterializableInAllContexts(AppView<AppInfoWithLiveness> appView) {
    return true;
  }

  @Override
  public InstanceFieldInitializationInfo fixupAfterParametersChanged(
      ArgumentInfoCollection argumentInfoCollection) {
    return this;
  }

  @Override
  public SingleValue rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, GraphLens lens, GraphLens codeLens) {
    return this;
  }
}
