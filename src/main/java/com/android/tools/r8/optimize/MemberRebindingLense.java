// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.GraphLense.NestedGraphLense;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

public class MemberRebindingLense extends NestedGraphLense {
  public static class Builder extends NestedGraphLense.Builder {
    private final AppInfo appInfo;

    protected Builder(AppInfo appInfo) {
      this.appInfo = appInfo;
    }

    public GraphLense build(GraphLense previousLense) {
      assert typeMap.isEmpty();
      if (methodMap.isEmpty() && fieldMap.isEmpty()) {
        return previousLense;
      }
      return new MemberRebindingLense(appInfo, methodMap, fieldMap, previousLense);
    }
  }

  private final AppInfo appInfo;

  public MemberRebindingLense(
      AppInfo appInfo,
      Map<DexMethod, DexMethod> methodMap,
      Map<DexField, DexField> fieldMap,
      GraphLense previousLense) {
    super(ImmutableMap.of(), methodMap, fieldMap, previousLense, appInfo.dexItemFactory);
    this.appInfo = appInfo;
  }

  public static Builder builder(AppInfo appInfo) {
    return new Builder(appInfo);
  }


  @Override
  protected Type mapInvocationType(
      DexMethod newMethod, DexMethod originalMethod, DexEncodedMethod context, Type type) {
    return super.mapVirtualInterfaceInvocationTypes(
        appInfo, newMethod, originalMethod, context, type);
  }
}
