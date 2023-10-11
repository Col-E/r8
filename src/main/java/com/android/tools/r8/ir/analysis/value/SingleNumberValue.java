// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexType;
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
  public boolean hasSingleMaterializingInstruction() {
    return true;
  }

  @Override
  public boolean maybeContainsInt(int value) {
    return value == getIntValue();
  }

  @Override
  public long getMinInclusive() {
    return value;
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

  @Override
  public int getDefinitelySetIntBits() {
    return getIntValue();
  }

  @Override
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
    if (other.isDefiniteBitsNumberValue()) {
      return true;
    }
    if (other.isSingleNumberValue()) {
      return equals(other.asSingleNumberValue());
    }
    assert other.isNumberFromIntervalValue() || other.isNumberFromSetValue();
    return other.asNonConstantNumberValue().maybeContainsInt(getIntValue());
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
  public Instruction[] createMaterializingInstructions(
      AppView<?> appView,
      ProgramMethod context,
      NumberGenerator valueNumberGenerator,
      TypeAndLocalInfoSupplier info) {
    ConstNumber materializingInstruction =
        createMaterializingInstruction(valueNumberGenerator, info);
    return new Instruction[] {materializingInstruction};
  }

  public ConstNumber createMaterializingInstruction(
      NumberGenerator valueNumberGenerator, TypeAndLocalInfoSupplier info) {
    return createMaterializingInstruction(
        valueNumberGenerator, info.getOutType(), info.getLocalInfo());
  }

  public ConstNumber createMaterializingInstruction(
      NumberGenerator valueNumberGenerator, TypeElement type) {
    return createMaterializingInstruction(valueNumberGenerator, type, null);
  }

  public ConstNumber createMaterializingInstruction(
      NumberGenerator valueNumberGenerator, TypeElement type, DebugLocalInfo localInfo) {
    assert type.isPrimitiveType();
    Value returnedValue = new Value(valueNumberGenerator.next(), type, localInfo);
    return ConstNumber.builder().setOutValue(returnedValue).setValue(value).build();
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
      AppView<AppInfoWithLiveness> appView, DexType newType, GraphLens lens, GraphLens codeLens) {
    return this;
  }
}
