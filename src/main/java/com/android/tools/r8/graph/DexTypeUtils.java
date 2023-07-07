// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.ir.analysis.type.ArrayTypeElement;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.utils.AndroidApiLevelUtils;
import com.google.common.collect.Iterables;

public class DexTypeUtils {

  public static DexType computeLeastUpperBound(
      AppView<? extends AppInfoWithClassHierarchy> appView, Iterable<DexType> types) {
    TypeElement join =
        TypeElement.join(Iterables.transform(types, type -> type.toTypeElement(appView)), appView);
    return findApiSafeUpperBound(appView, toDexType(appView, join));
  }

  public static DexType toDexType(
      AppView<? extends AppInfoWithClassHierarchy> appView, TypeElement type) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    if (type.isPrimitiveType()) {
      return type.asPrimitiveType().toDexType(dexItemFactory);
    }
    if (type.isArrayType()) {
      ArrayTypeElement arrayType = type.asArrayType();
      DexType baseType = toDexType(appView, arrayType.getBaseType());
      return baseType.toArrayType(arrayType.getNesting(), dexItemFactory);
    }
    assert type.isClassType();
    ClassTypeElement classType = type.asClassType();
    if (classType.getClassType() != dexItemFactory.objectType) {
      return classType.getClassType();
    }
    if (classType.getInterfaces().hasSingleKnownInterface()) {
      return classType.getInterfaces().getSingleKnownInterface();
    }
    return dexItemFactory.objectType;
  }

  public static DexType findApiSafeUpperBound(
      AppView<? extends AppInfoWithClassHierarchy> appView, DexType type) {
    DexItemFactory factory = appView.dexItemFactory();
    if (type.toBaseType(factory).isPrimitiveType()) {
      return type;
    }
    DexClass clazz = appView.definitionFor(type.isArrayType() ? type.toBaseType(factory) : type);
    if (clazz == null) {
      assert false : "We should not have found an upper bound if the hierarchy is missing";
      return type;
    }
    if (!clazz.isLibraryClass()
        || AndroidApiLevelUtils.isApiSafeForReference(clazz.asLibraryClass(), appView)) {
      return type;
    }
    // Always just return the object type since this is safe for all api versions.
    return factory.objectType;
  }
}
