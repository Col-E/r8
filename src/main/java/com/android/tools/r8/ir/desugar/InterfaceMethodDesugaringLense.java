// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.GraphLense.NestedGraphLense;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

class InterfaceMethodDesugaringLense extends NestedGraphLense {
  private final Map<DexEncodedMethod, DexEncodedMethod> methodsWithMovedCode;

  InterfaceMethodDesugaringLense(
      BiMap<DexMethod, DexMethod> methodMapping,
      Map<DexEncodedMethod, DexEncodedMethod> methodsWithMovedCode,
      GraphLense previous, DexItemFactory factory) {
    super(
        ImmutableMap.of(),
        methodMapping,
        ImmutableMap.of(),
        ImmutableBiMap.of(),
        methodMapping.inverse(),
        previous,
        factory);
    this.methodsWithMovedCode = methodsWithMovedCode;
  }

  @Override
  public DexEncodedMethod mapDexEncodedMethod(AppInfo appInfo, DexEncodedMethod original) {
    return super.mapDexEncodedMethod(
        appInfo, methodsWithMovedCode.getOrDefault(original, original));
  }
}
