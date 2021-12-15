// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner.multicallerinliner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.conversion.callgraph.CallGraphBase;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Map;

public class MultiCallerInlinerCallGraph extends CallGraphBase<MultiCallerInlinerNode> {

  MultiCallerInlinerCallGraph(Map<DexMethod, MultiCallerInlinerNode> nodes) {
    super(nodes);
  }

  public static MultiCallerInlinerCallGraphBuilder builder(AppView<AppInfoWithLiveness> appView) {
    return new MultiCallerInlinerCallGraphBuilder(appView);
  }
}
