// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.utils;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ObjectAllocationInfoCollection;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.DynamicTypeWithUpperBound;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class WideningUtils {

  public static DynamicType widenDynamicReceiverType(
      AppView<AppInfoWithLiveness> appView,
      ProgramMethod resolvedMethod,
      DynamicType dynamicReceiverType) {
    return internalWidenDynamicClassType(
        appView,
        dynamicReceiverType,
        resolvedMethod.getHolderType(),
        Nullability.definitelyNotNull());
  }

  public static DynamicType widenDynamicNonReceiverType(
      AppView<AppInfoWithLiveness> appView, DynamicType dynamicType, DexType staticType) {
    return widenDynamicNonReceiverType(appView, dynamicType, staticType, Nullability.maybeNull());
  }

  public static DynamicType widenDynamicNonReceiverType(
      AppView<AppInfoWithLiveness> appView,
      DynamicType dynamicType,
      DexType staticType,
      Nullability staticNullability) {
    return internalWidenDynamicClassType(appView, dynamicType, staticType, staticNullability);
  }

  private static DynamicType internalWidenDynamicClassType(
      AppView<AppInfoWithLiveness> appView,
      DynamicType dynamicType,
      DexType staticType,
      Nullability staticNullability) {
    assert staticType.isClassType();
    if (dynamicType.isBottom()
        || dynamicType.isNullType()
        || dynamicType.isNotNullType()
        || dynamicType.isUnknown()) {
      return dynamicType;
    }
    DynamicTypeWithUpperBound dynamicTypeWithUpperBound = dynamicType.asDynamicTypeWithUpperBound();
    TypeElement dynamicUpperBoundType = dynamicTypeWithUpperBound.getDynamicUpperBoundType();
    ClassTypeElement staticTypeElement =
        staticType.toTypeElement(appView).asClassType().getOrCreateVariant(staticNullability);
    if (dynamicType.getNullability().strictlyLessThan(staticNullability)) {
      if (dynamicType.getNullability().isDefinitelyNotNull()
          && dynamicUpperBoundType.equalUpToNullability(staticTypeElement)
          && hasTrivialLowerBound(appView, dynamicType, staticType)) {
        return DynamicType.definitelyNotNull();
      }
      return dynamicType;
    }
    if (!dynamicUpperBoundType.equals(staticTypeElement)) {
      return dynamicType;
    }
    if (!dynamicType.hasDynamicLowerBoundType()) {
      return DynamicType.unknown();
    }

    // If the static type does not have any program subtypes, then widen the dynamic type to
    // unknown.
    //
    // Note that if the static type is pinned, it could have subtypes outside the set of program
    // classes, but in this case it is still unlikely that we can use the dynamic lower bound type
    // information for anything, so we intentionally also widen to 'unknown' in this case.
    return isEffectivelyFinal(appView, staticType) ? DynamicType.unknown() : dynamicType;
  }

  private static boolean hasTrivialLowerBound(
      AppView<AppInfoWithLiveness> appView, DynamicType dynamicType, DexType staticType) {
    return !dynamicType.hasDynamicLowerBoundType() || isEffectivelyFinal(appView, staticType);
  }

  private static boolean isEffectivelyFinal(
      AppView<AppInfoWithLiveness> appView, DexType staticType) {
    DexClass staticTypeClass = appView.definitionFor(staticType);
    if (staticTypeClass == null) {
      return false;
    }
    if (!staticTypeClass.isProgramClass()) {
      return staticTypeClass.isFinal();
    }
    ObjectAllocationInfoCollection objectAllocationInfoCollection =
        appView.appInfo().getObjectAllocationInfoCollection();
    return !objectAllocationInfoCollection.hasInstantiatedStrictSubtype(
        staticTypeClass.asProgramClass());
  }
}
