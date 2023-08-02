// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.android.tools.r8.ir.analysis.type.TypeElement.stringClassType;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.TypeAndLocalInfoSupplier;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class SingleStringValue extends SingleConstValue {

  private final DexString string;

  /** Intentionally package private, use {@link AbstractValueFactory} instead. */
  SingleStringValue(DexString string) {
    this.string = string;
  }

  @Override
  public boolean isSingleStringValue() {
    return true;
  }

  @Override
  public SingleStringValue asSingleStringValue() {
    return this;
  }

  public DexString getDexString() {
    return string;
  }

  @Override
  public boolean equals(Object o) {
    return this == o;
  }

  @Override
  public int hashCode() {
    return string.hashCode();
  }

  @Override
  public String toString() {
    return "SingleStringValue(" + string + ")";
  }

  @Override
  public Instruction createMaterializingInstruction(
      AppView<?> appView,
      ProgramMethod context,
      NumberGenerator valueNumberGenerator,
      TypeAndLocalInfoSupplier info) {
    TypeElement typeLattice = info.getOutType();
    DebugLocalInfo debugLocalInfo = info.getLocalInfo();
    assert typeLattice.isClassType();
    assert appView
        .isSubtype(appView.dexItemFactory().stringType, typeLattice.asClassType().getClassType())
        .isTrue();
    Value returnedValue =
        new Value(
            valueNumberGenerator.next(),
            stringClassType(appView, definitelyNotNull()),
            debugLocalInfo);
    ConstString instruction = new ConstString(returnedValue, string);
    assert !instruction.instructionInstanceCanThrow(appView, context);
    return instruction;
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
