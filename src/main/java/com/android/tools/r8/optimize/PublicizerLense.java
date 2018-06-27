// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.GraphLense.NestedGraphLense;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Set;

final class PublicizerLense extends NestedGraphLense {
  private final AppInfo appInfo;
  private final Set<DexMethod> publicizedMethods;

  PublicizerLense(
      AppInfo appInfo, GraphLense previousLense, Set<DexMethod> publicizedMethods) {
    // This lense does not map any DexItem's at all.
    // It will just tweak invoke type for publicized methods from invoke-direct to invoke-virtual.
    super(ImmutableMap.of(), ImmutableMap.of(), ImmutableMap.of(),
        previousLense, appInfo.dexItemFactory);
    this.appInfo = appInfo;
    this.publicizedMethods = publicizedMethods;
  }

  @Override
  public GraphLenseLookupResult lookupMethod(
      DexMethod method, DexEncodedMethod context, Type type) {
    GraphLenseLookupResult previous = previousLense.lookupMethod(method, context, type);
    method = previous.getMethod();
    type = previous.getType();
    if (type == Type.DIRECT && publicizedMethods.contains(method)) {
      DexClass holderClass = appInfo.definitionFor(method.holder);
      if (holderClass != null) {
        DexEncodedMethod actualEncodedTarget = holderClass.lookupVirtualMethod(method);
        if (actualEncodedTarget != null
            && actualEncodedTarget.isPublicized()) {
          return new GraphLenseLookupResult(method, Type.VIRTUAL);
        }
      }
    }
    return super.lookupMethod(method, context, type);
  }

  static PublicizedLenseBuilder createBuilder() {
    return new PublicizedLenseBuilder();
  }

  static class PublicizedLenseBuilder {
    private final ImmutableSet.Builder<DexMethod> methodSetBuilder = ImmutableSet.builder();

    private PublicizedLenseBuilder() {
    }

    public GraphLense build(AppInfo appInfo, GraphLense previousLense) {
      return new PublicizerLense(appInfo, previousLense, methodSetBuilder.build());
    }

    public void add(DexMethod publicizedMethod) {
      methodSetBuilder.add(publicizedMethod);
    }
  }
}
