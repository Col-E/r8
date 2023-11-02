// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.callgraph;

import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class CallGraphBuilder extends IRProcessingCallGraphBuilderBase {

  public CallGraphBuilder(AppView<AppInfoWithLiveness> appView) {
    super(appView);
  }

  @Override
  void populateGraph(ExecutorService executorService) throws ExecutionException {
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        this::processClass,
        appView.options().getThreadingModule(),
        executorService);
  }

  private void processClass(DexProgramClass clazz) {
    clazz.forEachProgramMethodMatching(DexEncodedMethod::hasCode, this::processMethod);
  }

  private void processMethod(ProgramMethod method) {
    IRProcessingCallGraphUseRegistry<Node> registry =
        new IRProcessingCallGraphUseRegistry<>(
            appView,
            getOrCreateNode(method),
            this::getOrCreateNode,
            possibleProgramTargetsCache,
            alwaysTrue());
    method.registerCodeReferences(registry);
  }

  @Override
  boolean verifyAllMethodsWithCodeExists() {
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      for (DexEncodedMethod method : clazz.methods()) {
        assert method.hasCode() == (nodes.get(method.getReference()) != null);
      }
    }
    return true;
  }
}
