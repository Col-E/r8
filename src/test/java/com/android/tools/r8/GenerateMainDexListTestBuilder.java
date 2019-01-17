// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.GenerateMainDexListCommand.Builder;
import com.android.tools.r8.debug.DebugTestConfig;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.origin.Origin;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public class GenerateMainDexListTestBuilder
    extends TestBaseBuilder<
        GenerateMainDexListCommand,
        Builder,
        GenerateMainDexListResult,
        GenerateMainDexListRunResult,
        GenerateMainDexListTestBuilder> {

  private GenerateMainDexListTestBuilder(TestState state, Builder builder) {
    super(state, builder);
  }

  public static GenerateMainDexListTestBuilder create(TestState state) {
    return new GenerateMainDexListTestBuilder(state, GenerateMainDexListCommand.builder());
  }

  @Override
  GenerateMainDexListTestBuilder self() {
    return this;
  }

  @Override
  public GenerateMainDexListRunResult run(String mainClass)
      throws IOException, CompilationFailedException {
    throw new Unimplemented("No support for running with a main class");
  }

  @Override
  public GenerateMainDexListRunResult run(Class mainClass)
      throws IOException, CompilationFailedException {
    throw new Unimplemented("No support for running with a main class");
  }

  public DebugTestConfig debugConfig() {
    throw new Unimplemented("No support for debug configuration");
  }

  public GenerateMainDexListRunResult run() throws CompilationFailedException {
    return new GenerateMainDexListRunResult(GenerateMainDexList.run(builder.build()));
  }

  public GenerateMainDexListTestBuilder addMainDexRules(Collection<String> rules) {
    builder.addMainDexRules(new ArrayList<>(rules), Origin.unknown());
    return self();
  }

  public GenerateMainDexListTestBuilder addMainDexRules(String... rules) {
    return addMainDexRules(Arrays.asList(rules));
  }
}
