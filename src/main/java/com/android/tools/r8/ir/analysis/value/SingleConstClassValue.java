// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.android.tools.r8.ir.analysis.type.TypeElement.classClassType;

import com.android.tools.r8.features.ClassToFeatureSplitMap;
import com.android.tools.r8.graph.AccessControl;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.TypeAndLocalInfoSupplier;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class SingleConstClassValue extends SingleConstValue {

  private final DexType type;

  /** Intentionally package private, use {@link AbstractValueFactory} instead. */
  SingleConstClassValue(DexType type) {
    this.type = type;
  }

  @Override
  public boolean isSingleConstClassValue() {
    return true;
  }

  @Override
  public SingleConstClassValue asSingleConstClassValue() {
    return this;
  }

  public DexType getType() {
    return type;
  }

  @Override
  public boolean equals(Object o) {
    return this == o;
  }

  @Override
  public int hashCode() {
    return type.hashCode();
  }

  @Override
  public String toString() {
    return "SingleConstClassValue(" + type.toSourceString() + ")";
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
        .isSubtype(appView.dexItemFactory().classType, typeLattice.asClassType().getClassType())
        .isTrue();
    Value returnedValue =
        new Value(
            valueNumberGenerator.next(),
            classClassType(appView, definitelyNotNull()),
            debugLocalInfo);
    ConstClass instruction = new ConstClass(returnedValue, type);
    assert !instruction.instructionMayHaveSideEffects(appView, context);
    return instruction;
  }

  @Override
  boolean internalIsMaterializableInContext(
      AppView<? extends AppInfoWithClassHierarchy> appView, ProgramMethod context) {
    DexType baseType = type.toBaseType(appView.dexItemFactory());
    if (baseType.isClassType()) {
      DexClass clazz = appView.definitionFor(baseType);
      return clazz != null
          && clazz.isResolvable(appView)
          && AccessControl.isClassAccessible(clazz, context, appView).isTrue();
    }
    assert baseType.isPrimitiveType();
    return true;
  }

  @Override
  public boolean isMaterializableInAllContexts(AppView<AppInfoWithLiveness> appView) {
    DexType baseType = type.toBaseType(appView.dexItemFactory());
    if (baseType.isClassType()) {
      DexClass clazz = appView.definitionFor(baseType);
      if (clazz == null || !clazz.isPublic() || !clazz.isResolvable(appView)) {
        return false;
      }
      ClassToFeatureSplitMap classToFeatureSplitMap = appView.appInfo().getClassToFeatureSplitMap();
      if (clazz.isProgramClass()
          && classToFeatureSplitMap.isInFeature(clazz.asProgramClass(), appView)) {
        return false;
      }
      return true;
    }
    assert baseType.isPrimitiveType();
    return true;
  }

  @Override
  public InstanceFieldInitializationInfo fixupAfterParametersChanged(
      ArgumentInfoCollection argumentInfoCollection) {
    return this;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public SingleValue rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, DexType newType, GraphLens lens, GraphLens codeLens) {
    assert lens.lookupType(type, codeLens) == type;
    return this;
  }
}
