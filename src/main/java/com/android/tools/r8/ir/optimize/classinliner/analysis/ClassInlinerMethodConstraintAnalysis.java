// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.analysis;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.DataflowAnalysisResult.SuccessfulDataflowAnalysisResult;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.IntraproceduralDataflowAnalysis;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.optimize.classinliner.constraint.ClassInlinerMethodConstraint;
import com.android.tools.r8.ir.optimize.classinliner.constraint.ConditionalClassInlinerMethodConstraint;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class ClassInlinerMethodConstraintAnalysis {

  public static ClassInlinerMethodConstraint analyze(
      AppView<AppInfoWithLiveness> appView, ProgramMethod method, IRCode code) {
    if (method.getDefinition().isClassInitializer()
        || method.getDefinition().getNumberOfArguments() == 0) {
      return ClassInlinerMethodConstraint.alwaysFalse();
    }

    // Analyze code.
    IntraproceduralDataflowAnalysis<ParameterUsages> analysis =
        new IntraproceduralDataflowAnalysis<>(
            ParameterUsages.bottom(), new TransferFunction(appView, method));
    SuccessfulDataflowAnalysisResult<ParameterUsages> result =
        analysis.run(code.entryBlock()).asSuccessfulAnalysisResult();
    if (result == null) {
      return ClassInlinerMethodConstraint.alwaysFalse();
    }

    // TODO(b/181746071): This always returns a non-empty result, that should be transformed into
    //  bottom or top.
    ParameterUsages usages = result.join();
    if (usages.isBottom()) {
      return ClassInlinerMethodConstraint.alwaysTrue();
    }
    if (usages.isTop()) {
      return ClassInlinerMethodConstraint.alwaysFalse();
    }
    return new ConditionalClassInlinerMethodConstraint(usages.externalize());
  }
}
