// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.value;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.TypeAndLocalInfoSupplier;

public class SingleBoxedLongValue extends SingleBoxedNumberValue {

  private final long value;

  SingleBoxedLongValue(long value) {
    this.value = value;
  }

  @Override
  public Instruction[] createMaterializingInstructions(
      AppView<?> appView,
      ProgramMethod context,
      NumberGenerator numberGenerator,
      TypeAndLocalInfoSupplier info) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    ConstNumber constNumber =
        ConstNumber.builder()
            .setFreshOutValue(numberGenerator, getPrimitiveType())
            .setValue(value)
            .build();
    InvokeStatic invokeStatic =
        InvokeStatic.builder()
            .setFreshOutValue(numberGenerator, getBoxedPrimitiveType(appView), info.getLocalInfo())
            .setMethod(dexItemFactory.longMembers.valueOf)
            .setSingleArgument(constNumber.outValue())
            .build();
    return new Instruction[] {constNumber, invokeStatic};
  }

  @Override
  public TypeElement getBoxedPrimitiveType(AppView<?> appView) {
    return appView.dexItemFactory().boxedLongType.toTypeElement(appView);
  }

  @Override
  public TypeElement getPrimitiveType() {
    return TypeElement.getLong();
  }

  @Override
  public long getRawValue() {
    return value;
  }

  @Override
  public boolean hasSingleMaterializingInstruction() {
    return false;
  }

  @Override
  public boolean isSingleBoxedLong() {
    return true;
  }

  @Override
  public SingleBoxedLongValue asSingleBoxedLong() {
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SingleBoxedLongValue)) {
      return false;
    }
    SingleBoxedLongValue singleBoxedLongValue = (SingleBoxedLongValue) o;
    return value == singleBoxedLongValue.value;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(value);
  }

  @Override
  public String toString() {
    return "SingleBoxedLongValue(" + value + ")";
  }
}
