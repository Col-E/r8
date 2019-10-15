// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class PartialCallGraphBuilder extends CallGraphBuilderBase {
  private final Set<DexEncodedMethod> seeds;

  PartialCallGraphBuilder(AppView<AppInfoWithLiveness> appView, Set<DexEncodedMethod> seeds) {
    super(appView);
    assert seeds != null && !seeds.isEmpty();
    this.seeds = seeds;
  }

  @Override
  void process(ExecutorService executorService) throws ExecutionException {
    List<Future<?>> futures = new ArrayList<>();
    for (DexEncodedMethod method : seeds) {
      futures.add(
          executorService.submit(
              () -> {
                processMethod(method);
                return null; // we want a Callable not a Runnable to be able to throw
              }));
    }
    ThreadUtils.awaitFutures(futures);
  }

  private void processMethod(DexEncodedMethod method) {
    if (method.hasCode()) {
      method.registerCodeReferences(
          new InvokeExtractor(getOrCreateNode(method), seeds::contains));
    }
  }

  @Override
  boolean verifyAllMethodsWithCodeExists() {
    for (DexEncodedMethod method : seeds) {
      assert !method.hasCode() || nodes.get(method.method) != null;
    }
    return true;
  }
}
