// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.utils;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepMethodInfo;
import com.android.tools.r8.utils.InternalOptions;

public class ParameterRemovalUtils {

  public static boolean canRemoveUnusedParametersFrom(
      AppView<AppInfoWithLiveness> appView, ProgramMethod method) {
    KeepMethodInfo keepInfo = appView.getKeepInfo(method);
    InternalOptions options = appView.options();
    if (!keepInfo.isParameterRemovalAllowed(options)) {
      return false;
    }
    if (appView.appInfo().isKeepUnusedArgumentsMethod(method)) {
      return false;
    }
    return method.getDefinition().isLibraryMethodOverride().isFalse()
        && !appView.appInfoWithLiveness().isMethodTargetedByInvokeDynamic(method);
  }

  public static boolean canRemoveUnusedParameter(
      AppView<AppInfoWithLiveness> appView, ProgramMethod method, int argumentIndex) {
    assert canRemoveUnusedParametersFrom(appView, method);
    if (argumentIndex == 0) {
      if (method.getDefinition().isInstanceInitializer()) {
        return false;
      }
      if (method.getDefinition().isInstance()) {
        if (method.getAccessFlags().isSynchronized()) {
          return false;
        }
        KeepMethodInfo keepInfo = appView.getKeepInfo(method);
        InternalOptions options = appView.options();
        if (!keepInfo.isMethodStaticizingAllowed(options)) {
          return false;
        }
      }
    }
    return true;
  }
}
