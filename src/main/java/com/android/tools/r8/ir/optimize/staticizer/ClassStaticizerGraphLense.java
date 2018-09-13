// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.staticizer;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.GraphLense.NestedGraphLense;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

class ClassStaticizerGraphLense extends NestedGraphLense {
  private final Map<DexEncodedMethod, DexEncodedMethod> staticizedMethods;

  ClassStaticizerGraphLense(
      GraphLense previous,
      DexItemFactory factory,
      BiMap<DexField, DexField> fieldMapping,
      BiMap<DexMethod, DexMethod> methodMapping,
      Map<DexEncodedMethod, DexEncodedMethod> encodedMethodMapping) {
    super(ImmutableMap.of(),
        methodMapping,
        fieldMapping,
        fieldMapping.inverse(),
        methodMapping.inverse(),
        previous,
        factory);
    staticizedMethods = encodedMethodMapping;
  }

  @Override
  protected Type mapInvocationType(
      DexMethod newMethod, DexMethod originalMethod,
      DexEncodedMethod context, Type type) {
    if (methodMap.get(originalMethod) == newMethod) {
      assert type == Type.VIRTUAL || type == Type.DIRECT;
      return Type.STATIC;
    }
    return super.mapInvocationType(newMethod, originalMethod, context, type);
  }

  @Override
  public DexEncodedMethod mapDexEncodedMethod(AppInfo appInfo, DexEncodedMethod original) {
    return super.mapDexEncodedMethod(appInfo, staticizedMethods.getOrDefault(original, original));
  }
}
