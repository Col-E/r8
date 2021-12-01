// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;

public class AccessUtils {

  public static boolean isAccessibleInSameContextsAs(
      DexType newType, DexType oldType, DexDefinitionSupplier definitions) {
    DexItemFactory dexItemFactory = definitions.dexItemFactory();
    DexType newBaseType = newType.toBaseType(dexItemFactory);
    if (!newBaseType.isClassType()) {
      return true;
    }
    DexClass newBaseClass = definitions.definitionFor(newBaseType);
    if (newBaseClass == null) {
      return false;
    }
    if (newBaseClass.isPublic()) {
      return true;
    }
    DexType oldBaseType = oldType.toBaseType(dexItemFactory);
    assert oldBaseType.isClassType();
    DexClass oldBaseClass = definitions.definitionFor(oldBaseType);
    if (oldBaseClass == null || oldBaseClass.isPublic()) {
      return false;
    }
    return newBaseType.isSamePackage(oldBaseType);
  }
}
