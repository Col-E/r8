// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.utils.Pair;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class DesugarTestBuilder
    extends TestBuilderCollection<
        DesugarTestConfiguration, DesugarTestRunResult, DesugarTestBuilder> {

  public static DesugarTestBuilder create(
      TestState state,
      List<Pair<DesugarTestConfiguration, TestBuilder<? extends TestRunResult<?>, ?>>>
          testBuilders) {
    return new DesugarTestBuilder(state, testBuilders);
  }

  private DesugarTestBuilder(
      TestState state,
      List<Pair<DesugarTestConfiguration, TestBuilder<? extends TestRunResult<?>, ?>>> builders) {
    super(state, builders);
  }

  @Override
  DesugarTestBuilder self() {
    return this;
  }

  @Override
  public DesugarTestRunResult run(TestRuntime runtime, String mainClass, String... args)
      throws CompilationFailedException, ExecutionException, IOException {
    List<Pair<DesugarTestConfiguration, TestRunResult<?>>> runs = new ArrayList<>(builders.size());
    for (Pair<DesugarTestConfiguration, TestBuilder<? extends TestRunResult<?>, ?>> builder :
        builders) {
      runs.add(new Pair<>(builder.getFirst(), builder.getSecond().run(runtime, mainClass, args)));
    }
    return DesugarTestRunResult.create(runs);
  }
}
