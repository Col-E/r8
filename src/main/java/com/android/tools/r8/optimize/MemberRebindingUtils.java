// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;

public class MemberRebindingUtils {

  @SuppressWarnings("ReferenceEquality")
  public static boolean isNonReboundMethodReference(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      DexMethod method,
      ProgramMethod context) {
    DexClass clazz = appView.definitionForHolder(method, context);
    if (clazz == null) {
      return false;
    }
    SingleResolutionResult<?> resolutionResult =
        appView.appInfo().resolveMethodOnLegacy(clazz, method).asSingleResolution();
    return resolutionResult != null
        && resolutionResult.getResolvedHolder().getType() != method.getHolderType();
  }
}
