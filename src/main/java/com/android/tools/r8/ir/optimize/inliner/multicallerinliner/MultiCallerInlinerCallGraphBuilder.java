// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner.multicallerinliner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.callgraph.CallGraphBuilderBase;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class MultiCallerInlinerCallGraphBuilder
    extends CallGraphBuilderBase<MultiCallerInlinerNode> {

  MultiCallerInlinerCallGraphBuilder(AppView<AppInfoWithLiveness> appView) {
    super(appView);
  }

  @Override
  protected MultiCallerInlinerNode createNode(ProgramMethod method) {
    return new MultiCallerInlinerNode(method);
  }

  public MultiCallerInlinerCallGraph build(ExecutorService executorService)
      throws ExecutionException {
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        this::processClass,
        appView.options().getThreadingModule(),
        executorService);
    return new MultiCallerInlinerCallGraph(nodes);
  }

  private void processClass(DexProgramClass clazz) {
    clazz.forEachProgramMethodMatching(DexEncodedMethod::hasCode, this::processMethod);
  }

  private void processMethod(ProgramMethod method) {
    MultiCallerInlinerInvokeRegistry registry =
        new MultiCallerInlinerInvokeRegistry(
            appView, getOrCreateNode(method), this::getOrCreateNode, possibleProgramTargetsCache);
    method.registerCodeReferences(registry);
  }
}
