// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value;

import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;
import static com.android.tools.r8.utils.ForEachUtils.allMatch;

import com.android.tools.r8.features.ClassToFeatureSplitMap;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.objectstate.ObjectState;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.TypeAndLocalInfoSupplier;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.enums.EnumDataMap;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.ObjectAllocationInfoCollectionUtils;

public abstract class SingleFieldValue extends SingleValue {

  final DexField field;

  SingleFieldValue(DexField field) {
    this.field = field;
  }

  public DexField getField() {
    return field;
  }

  public DexEncodedField getField(DexDefinitionSupplier definitions) {
    DexClass holder = definitions.definitionFor(field.getHolderType());
    return field.lookupOnClass(holder);
  }

  @Override
  public abstract ObjectState getObjectState();

  @Override
  public abstract boolean hasObjectState();

  public boolean mayHaveFinalizeMethodDirectlyOrIndirectly(AppView<AppInfoWithLiveness> appView) {
    DexType fieldType = field.type;
    if (fieldType.isClassType()) {
      ClassTypeElement fieldClassType =
          TypeElement.fromDexType(fieldType, maybeNull(), appView).asClassType();
      return ObjectAllocationInfoCollectionUtils.mayHaveFinalizeMethodDirectlyOrIndirectly(
          appView, fieldClassType);
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
  public abstract boolean equals(Object o);

  @Override
  public abstract int hashCode();

  @Override
  public Instruction createMaterializingInstruction(
      AppView<?> appView,
      ProgramMethod context,
      NumberGenerator valueNumberGenerator,
      TypeAndLocalInfoSupplier info) {
    TypeElement type = TypeElement.fromDexType(field.type, maybeNull(), appView);
    assert type.lessThanOrEqual(info.getOutType(), appView)
        || !appView.enableWholeProgramOptimizations()
        || type.isBasedOnMissingClass(appView.withClassHierarchy());
    Value outValue = new Value(valueNumberGenerator.next(), type, info.getLocalInfo());
    return new StaticGet(outValue, field);
  }

  @Override
  boolean internalIsMaterializableInContext(
      AppView<? extends AppInfoWithClassHierarchy> appView, ProgramMethod context) {
    return allMatch(
        appView.appInfo().resolveField(field)::forEachFieldResolutionResult,
        resolutionResult -> {
          if (resolutionResult.isPossiblyFailedOrUnknownResolution()) {
            return false;
          }
          return resolutionResult.isAccessibleFrom(context, appView).isTrue();
        });
  }

  @Override
  public boolean isMaterializableInAllContexts(AppView<AppInfoWithLiveness> appView) {
    DexEncodedField encodedField = appView.appInfo().resolveField(field).getResolvedField();
    if (encodedField == null) {
      assert false;
      return false;
    }
    if (!encodedField.isPublic()) {
      return false;
    }
    DexClass holder = appView.definitionFor(encodedField.getHolderType());
    if (holder == null) {
      assert false;
      return false;
    }
    if (!holder.isPublic()) {
      return false;
    }
    ClassToFeatureSplitMap classToFeatureSplitMap = appView.appInfo().getClassToFeatureSplitMap();
    if (holder.isProgramClass()
        && classToFeatureSplitMap.isInFeature(holder.asProgramClass(), appView)) {
      return false;
    }
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
      AppView<AppInfoWithLiveness> appView, GraphLens lens, GraphLens codeLens) {
    AbstractValueFactory factory = appView.abstractValueFactory();
    EnumDataMap enumDataMap = appView.unboxedEnums();
    if (enumDataMap.hasUnboxedValueFor(field)) {
      return factory.createSingleNumberValue(
          enumDataMap.getUnboxedValue(field), TypeElement.getInt());
    }
    DexField rewrittenField = lens.lookupField(field, codeLens);
    ObjectState rewrittenObjectState = getObjectState().rewrittenWithLens(appView, lens, codeLens);
    if (rewrittenField != field || rewrittenObjectState != getObjectState()) {
      return factory.createSingleFieldValue(rewrittenField, rewrittenObjectState);
    }
    return this;
  }
}
