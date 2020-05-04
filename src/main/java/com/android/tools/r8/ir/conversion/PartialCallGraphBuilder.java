// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class PartialCallGraphBuilder extends CallGraphBuilderBase {

  private final Map<DexEncodedMethod, ProgramMethod> seeds;

  PartialCallGraphBuilder(
      AppView<AppInfoWithLiveness> appView, Map<DexEncodedMethod, ProgramMethod> seeds) {
    super(appView);
    assert seeds != null && !seeds.isEmpty();
    this.seeds = seeds;
  }

  @Override
  void populateGraph(ExecutorService executorService) throws ExecutionException {
    ThreadUtils.processItems(seeds.values(), this::processMethod, executorService);
  }

  private void processMethod(ProgramMethod method) {
    method.registerCodeReferences(
        new InvokeExtractor(
            getOrCreateNode(method), other -> seeds.containsKey(other.getDefinition())));
  }

  @Override
  boolean verifyAllMethodsWithCodeExists() {
    for (ProgramMethod method : seeds.values()) {
      assert method.getDefinition().hasCode() == (nodes.get(method.getReference()) != null);
    }
    return true;
  }
}
