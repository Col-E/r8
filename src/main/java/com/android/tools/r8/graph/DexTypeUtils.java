// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.InterfaceCollection;
import com.android.tools.r8.utils.ObjectUtils;
import java.util.Iterator;

public class DexTypeUtils {

  public static DexType computeLeastUpperBound(
      AppView<? extends AppInfoWithClassHierarchy> appView, Iterable<DexType> types) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();

    Iterator<DexType> iterator = types.iterator();
    assert iterator.hasNext();

    DexType result = iterator.next();
    while (iterator.hasNext()) {
      result = computeLeastUpperBound(appView, result, iterator.next());
    }
    return result;
  }

  public static DexType computeLeastUpperBound(
      AppView<? extends AppInfoWithClassHierarchy> appView, DexType type, DexType other) {
    if (type == other) {
      return type;
    }

    DexItemFactory dexItemFactory = appView.dexItemFactory();
    if (type == dexItemFactory.objectType
        || other == dexItemFactory.objectType
        || type.isArrayType() != other.isArrayType()) {
      return dexItemFactory.objectType;
    }

    if (type.isArrayType()) {
      assert other.isArrayType();
      int arrayDimension = type.getNumberOfLeadingSquareBrackets();
      if (other.getNumberOfLeadingSquareBrackets() != arrayDimension) {
        return dexItemFactory.objectType;
      }

      DexType baseType = type.toBaseType(dexItemFactory);
      DexType otherBaseType = other.toBaseType(dexItemFactory);
      if (baseType.isPrimitiveType() || otherBaseType.isPrimitiveType()) {
        assert baseType != otherBaseType;
        return dexItemFactory.objectType;
      }

      return dexItemFactory.createArrayType(
          arrayDimension, computeLeastUpperBound(appView, baseType, otherBaseType));
    }

    assert !type.isArrayType();
    assert !other.isArrayType();

    boolean isInterface =
        type.isClassType()
            && ObjectUtils.getBooleanOrElse(
                appView.definitionFor(type), DexClass::isInterface, false);
    boolean otherIsInterface =
        other.isClassType()
            && ObjectUtils.getBooleanOrElse(
                appView.definitionFor(other), DexClass::isInterface, false);
    if (isInterface != otherIsInterface) {
      return dexItemFactory.objectType;
    }

    if (isInterface) {
      assert otherIsInterface;
      InterfaceCollection interfaceCollection =
          ClassTypeElement.computeLeastUpperBoundOfInterfaces(
              appView, InterfaceCollection.singleton(type), InterfaceCollection.singleton(other));
      return interfaceCollection.hasSingleKnownInterface()
          ? interfaceCollection.getSingleKnownInterface()
          : dexItemFactory.objectType;
    }

    assert !isInterface;
    assert !otherIsInterface;

    return ClassTypeElement.computeLeastUpperBoundOfClasses(appView.appInfo(), type, other);
  }
}
