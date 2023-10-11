// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.value;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.TypeAndLocalInfoSupplier;
import com.android.tools.r8.utils.BooleanUtils;

public class SingleBoxedBooleanValue extends SingleBoxedNumberValue {

  private static final SingleBoxedBooleanValue FALSE_INSTANCE = new SingleBoxedBooleanValue(false);
  private static final SingleBoxedBooleanValue TRUE_INSTANCE = new SingleBoxedBooleanValue(true);

  private final boolean value;

  SingleBoxedBooleanValue(boolean value) {
    this.value = value;
  }

  static SingleBoxedBooleanValue getFalseInstance() {
    return FALSE_INSTANCE;
  }

  static SingleBoxedBooleanValue getTrueInstance() {
    return TRUE_INSTANCE;
  }

  @Override
  public Instruction[] createMaterializingInstructions(
      AppView<?> appView,
      ProgramMethod context,
      NumberGenerator numberGenerator,
      TypeAndLocalInfoSupplier info) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    StaticGet staticGet =
        StaticGet.builder()
            .setFreshOutValue(numberGenerator, getBoxedPrimitiveType(appView), info.getLocalInfo())
            .setField(
                value ? dexItemFactory.booleanMembers.TRUE : dexItemFactory.booleanMembers.FALSE)
            .build();
    return new Instruction[] {staticGet};
  }

  @Override
  public TypeElement getBoxedPrimitiveType(AppView<?> appView) {
    return appView.dexItemFactory().boxedBooleanType.toTypeElement(appView);
  }

  @Override
  public TypeElement getPrimitiveType() {
    return TypeElement.getInt();
  }

  public int getIntValue() {
    return BooleanUtils.intValue(value);
  }

  @Override
  public long getRawValue() {
    return getIntValue();
  }

  @Override
  public boolean hasSingleMaterializingInstruction() {
    return true;
  }

  @Override
  public boolean isSingleBoxedBoolean() {
    return true;
  }

  @Override
  public SingleBoxedBooleanValue asSingleBoxedBoolean() {
    return this;
  }

  @Override
  public boolean equals(Object o) {
    return this == o;
  }

  @Override
  public int hashCode() {
    return getIntValue();
  }

  @Override
  public String toString() {
    return "SingleBoxedBooleanValue(" + value + ")";
  }
}
