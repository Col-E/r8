// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldvalueanalysis;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.optimize.ClassInitializerDefaultsOptimization.ClassInitializerDefaultsResult;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class InstanceFieldValueAnalysis extends FieldValueAnalysis {

  private InstanceFieldValueAnalysis(
      AppView<AppInfoWithLiveness> appView,
      IRCode code,
      OptimizationFeedback feedback,
      DexProgramClass clazz,
      DexEncodedMethod method) {
    super(appView, code, feedback, clazz, method);
  }

  public static void run(
      AppView<?> appView,
      IRCode code,
      ClassInitializerDefaultsResult classInitializerDefaultsResult,
      OptimizationFeedback feedback,
      DexEncodedMethod method) {
    assert appView.appInfo().hasLiveness();
    assert appView.enableWholeProgramOptimizations();
    assert method.isInstanceInitializer();
    DexProgramClass clazz = appView.definitionFor(method.method.holder).asProgramClass();
    if (!appView.options().enableValuePropagationForInstanceFields) {
      return;
    }
    DexEncodedMethod otherInstanceInitializer =
        clazz.lookupDirectMethod(other -> other.isInstanceInitializer() && other != method);
    if (otherInstanceInitializer != null) {
      // Conservatively bail out.
      // TODO(b/125282093): Handle multiple instance initializers on the same class.
      return;
    }
    new InstanceFieldValueAnalysis(appView.withLiveness(), code, feedback, clazz, method)
        .computeFieldOptimizationInfo(classInitializerDefaultsResult);
  }
}
