// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.staticizer;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.NestedGraphLens;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneMap;

class ClassStaticizerGraphLens extends NestedGraphLens {

  ClassStaticizerGraphLens(
      AppView<?> appView,
      BidirectionalOneToOneMap<DexField, DexField> fieldMapping,
      BidirectionalOneToOneMap<DexMethod, DexMethod> methodMapping) {
    super(appView, fieldMapping, methodMapping, EMPTY_TYPE_MAP);
  }

  @Override
  protected Type mapInvocationType(DexMethod newMethod, DexMethod originalMethod, Type type) {
    if (methodMap.apply(originalMethod) == newMethod) {
      assert type == Type.VIRTUAL || type == Type.DIRECT;
      return Type.STATIC;
    }
    return super.mapInvocationType(newMethod, originalMethod, type);
  }
}
