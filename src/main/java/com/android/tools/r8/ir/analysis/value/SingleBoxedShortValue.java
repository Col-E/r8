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
import com.android.tools.r8.ir.code.MaterializingInstructionsInfo;
import com.android.tools.r8.ir.code.ValueFactory;

public class SingleBoxedShortValue extends SingleBoxedNumberValue {

  private final int value;

  SingleBoxedShortValue(int value) {
    this.value = value;
  }

  @Override
  public Instruction[] createMaterializingInstructions(
      AppView<?> appView,
      ProgramMethod context,
      ValueFactory valueFactory,
      MaterializingInstructionsInfo info) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    ConstNumber constNumber =
        ConstNumber.builder()
            .setFreshOutValue(valueFactory, getPrimitiveType())
            .setPositionForNonThrowingInstruction(info.getPosition(), appView.options())
            .setValue(value)
            .build();
    InvokeStatic invokeStatic =
        InvokeStatic.builder()
            .setFreshOutValue(valueFactory, getBoxedPrimitiveType(appView), info.getLocalInfo())
            .setMethod(dexItemFactory.shortMembers.valueOf)
            .setPosition(info.getPosition())
            .setSingleArgument(constNumber.outValue())
            .build();
    return new Instruction[] {constNumber, invokeStatic};
  }

  @Override
  public TypeElement getBoxedPrimitiveType(AppView<?> appView) {
    return appView.dexItemFactory().boxedShortType.toTypeElement(appView);
  }

  @Override
  public TypeElement getPrimitiveType() {
    return TypeElement.getInt();
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
  public boolean isSingleBoxedShort() {
    return true;
  }

  @Override
  public SingleBoxedShortValue asSingleBoxedShort() {
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SingleBoxedShortValue)) {
      return false;
    }
    SingleBoxedShortValue singleBoxedShortValue = (SingleBoxedShortValue) o;
    return value == singleBoxedShortValue.value;
  }

  @Override
  public int hashCode() {
    return value;
  }

  @Override
  public String toString() {
    return "SingleBoxedShortValue(" + value + ")";
  }
}
