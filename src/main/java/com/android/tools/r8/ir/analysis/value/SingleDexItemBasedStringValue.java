// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.android.tools.r8.ir.analysis.type.TypeElement.stringClassType;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.DexItemBasedConstString;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.TypeAndLocalInfoSupplier;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfo;
import com.android.tools.r8.naming.dexitembasedstring.NameComputationInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Objects;

public class SingleDexItemBasedStringValue extends SingleConstValue {

  private final DexReference item;
  private final NameComputationInfo<?> nameComputationInfo;

  /** Intentionally package private, use {@link AbstractValueFactory} instead. */
  SingleDexItemBasedStringValue(DexReference item, NameComputationInfo<?> nameComputationInfo) {
    this.item = item;
    this.nameComputationInfo = nameComputationInfo;
  }

  @Override
  public boolean hasSingleMaterializingInstruction() {
    return true;
  }

  public DexReference getItem() {
    return item;
  }

  public NameComputationInfo<?> getNameComputationInfo() {
    return nameComputationInfo;
  }

  @Override
  public boolean isSingleDexItemBasedStringValue() {
    return true;
  }

  @Override
  public SingleDexItemBasedStringValue asSingleDexItemBasedStringValue() {
    return this;
  }

  @Override
  @SuppressWarnings({"EqualsGetClass", "ReferenceEquality"})
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SingleDexItemBasedStringValue value = (SingleDexItemBasedStringValue) o;
    return item == value.item && nameComputationInfo == value.nameComputationInfo;
  }

  @Override
  public int hashCode() {
    return Objects.hash(item, nameComputationInfo);
  }

  @Override
  public String toString() {
    return "DexItemBasedConstString(" + item.toSourceString() + ")";
  }

  @Override
  public Instruction[] createMaterializingInstructions(
      AppView<?> appView,
      ProgramMethod context,
      NumberGenerator numberGenerator,
      TypeAndLocalInfoSupplier info) {
    DebugLocalInfo localInfo = info.getLocalInfo();
    TypeElement stringType = stringClassType(appView, definitelyNotNull());
    assert stringType.lessThanOrEqual(info.getOutType(), appView);
    Value returnedValue = new Value(numberGenerator.next(), stringType, localInfo);
    DexItemBasedConstString constString =
        new DexItemBasedConstString(returnedValue, item, nameComputationInfo);
    assert !constString.instructionInstanceCanThrow(appView, context);
    return new Instruction[] {constString};
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
    return appView
        .abstractValueFactory()
        .createSingleDexItemBasedStringValue(
            lens.rewriteReference(item, codeLens), nameComputationInfo);
  }
}
