// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner.multicallerinliner;

import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.conversion.callgraph.InvokeExtractor;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.Map;
import java.util.function.Function;

public class MultiCallerInlinerInvokeRegistry extends InvokeExtractor<MultiCallerInlinerNode> {

  MultiCallerInlinerInvokeRegistry(
      AppView<AppInfoWithLiveness> appView,
      MultiCallerInlinerNode currentMethod,
      Function<ProgramMethod, MultiCallerInlinerNode> nodeFactory,
      Map<DexMethod, ProgramMethodSet> possibleProgramTargetsCache) {
    super(appView, currentMethod, nodeFactory, possibleProgramTargetsCache, alwaysTrue());
  }

  @Override
  public GraphLens getCodeLens() {
    return appViewWithLiveness.graphLens();
  }

  @Override
  protected void processInvokeWithDynamicDispatch(
      InvokeType type, DexClassAndMethod resolutionResult, ProgramMethod context) {
    // Skip calls that dispatch to library methods or library method overrides.
    if (resolutionResult.isProgramMethod()
        && resolutionResult.getDefinition().isLibraryMethodOverride().isPossiblyFalse()) {
      super.processInvokeWithDynamicDispatch(type, resolutionResult, context);
    }
  }
}
