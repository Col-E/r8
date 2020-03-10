// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value;

import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;
import static com.android.tools.r8.optimize.MemberRebindingAnalysis.isMemberVisibleFromOriginalContext;

import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.ir.analysis.type.ClassTypeLatticeElement;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.TypeAndLocalInfoSupplier;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class SingleFieldValue extends SingleValue {

  private final DexField field;

  /** Intentionally package private, use {@link AbstractValueFactory} instead. */
  SingleFieldValue(DexField field) {
    this.field = field;
  }

  public DexField getField() {
    return field;
  }

  public boolean mayHaveFinalizeMethodDirectlyOrIndirectly(AppView<AppInfoWithLiveness> appView) {
    DexType fieldType = field.type;
    if (fieldType.isClassType()) {
      ClassTypeLatticeElement fieldClassType =
          TypeLatticeElement.fromDexType(fieldType, maybeNull(), appView)
              .asClassTypeLatticeElement();
      return appView.appInfo().mayHaveFinalizeMethodDirectlyOrIndirectly(fieldClassType);
    }
    assert fieldType.isArrayType() || fieldType.isPrimitiveType();
    return false;
  }

  @Override
  public boolean isSingleFieldValue() {
    return true;
  }

  @Override
  public SingleFieldValue asSingleFieldValue() {
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
    return "SingleFieldValue(" + field.toSourceString() + ")";
  }

  @Override
  public Instruction createMaterializingInstruction(
      AppView<? extends AppInfoWithSubtyping> appView, IRCode code, TypeAndLocalInfoSupplier info) {
    TypeLatticeElement type = TypeLatticeElement.fromDexType(field.type, maybeNull(), appView);
    assert type.lessThanOrEqual(info.getTypeLattice(), appView);
    Value outValue = code.createValue(type, info.getLocalInfo());
    return new StaticGet(outValue, field);
  }

  @Override
  public boolean isMaterializableInContext(AppView<?> appView, DexType context) {
    DexEncodedField encodedField = appView.appInfo().resolveField(field);
    return isMemberVisibleFromOriginalContext(
        appView, context, encodedField.field.holder, encodedField.accessFlags);
  }

  @Override
  public boolean isMaterializableInAllContexts(AppView<?> appView) {
    DexEncodedField encodedField = appView.appInfo().resolveField(field);
    if (encodedField == null) {
      assert false;
      return false;
    }
    if (!encodedField.isPublic()) {
      return false;
    }
    DexClass holder = appView.definitionFor(encodedField.holder());
    if (holder == null) {
      assert false;
      return false;
    }
    return holder.isPublic();
  }

  @Override
  public SingleValue rewrittenWithLens(AppView<AppInfoWithLiveness> appView, GraphLense lens) {
    DexField rewrittenField = lens.lookupField(field);
    assert !appView.unboxedEnums().containsEnum(field.holder)
        || !appView.appInfo().resolveField(rewrittenField).accessFlags.isEnum();
    return appView.abstractValueFactory().createSingleFieldValue(rewrittenField);
  }
}
