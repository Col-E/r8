// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
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

public class SingleNullValue extends SingleConstValue {

  private static final SingleNullValue INSTANCE = new SingleNullValue();

  /** Intentionally package private, use {@link AbstractValueFactory} instead. */
  SingleNullValue() {}

  static SingleNullValue get() {
    return INSTANCE;
  }

  @Override
  public boolean hasSingleMaterializingInstruction() {
    return true;
  }

  @Override
  public boolean isNull() {
    return true;
  }

  @Override
  public SingleNullValue asSingleNullValue() {
    return this;
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
    return "SingleNullValue";
  }

  @Override
  public Instruction[] createMaterializingInstructions(
      AppView<?> appView,
      ProgramMethod context,
      NumberGenerator numberGenerator,
      TypeAndLocalInfoSupplier info) {
    ConstNumber materializingInstruction = createMaterializingInstruction(numberGenerator, info);
    return new Instruction[] {materializingInstruction};
  }

  public ConstNumber createMaterializingInstruction(
      NumberGenerator numberGenerator, TypeAndLocalInfoSupplier info) {
    assert info.getOutType().isReferenceType() : info.getOutType();
    DebugLocalInfo localInfo = info.getLocalInfo();
    Value returnedValue = new Value(numberGenerator.next(), TypeElement.getNull(), localInfo);
    return new ConstNumber(returnedValue, 0);
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
    if (newType.isIntType()) {
      // Enum unboxing.
      return appView.abstractValueFactory().createZeroValue();
    }
    return this;
  }
}
