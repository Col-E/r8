// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.callgraph;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class PartialCallGraphBuilder extends IRProcessingCallGraphBuilderBase {

  private final ProgramMethodSet seeds;

  public PartialCallGraphBuilder(AppView<AppInfoWithLiveness> appView, ProgramMethodSet seeds) {
    super(appView);
    assert seeds != null && !seeds.isEmpty();
    this.seeds = seeds;
  }

  @Override
  void populateGraph(ExecutorService executorService) throws ExecutionException {
    ThreadUtils.processItems(
        seeds, this::processMethod, appView.options().getThreadingModule(), executorService);
  }

  private void processMethod(ProgramMethod method) {
    IRProcessingCallGraphUseRegistry<Node> registry =
        new IRProcessingCallGraphUseRegistry<>(
            appView,
            getOrCreateNode(method),
            this::getOrCreateNode,
            possibleProgramTargetsCache,
            seeds::contains);
    method.registerCodeReferences(registry);
  }

  @Override
  boolean verifyAllMethodsWithCodeExists() {
    for (ProgramMethod method : seeds) {
      assert method.getDefinition().hasCode() == (nodes.get(method.getReference()) != null);
    }
    return true;
  }
}
