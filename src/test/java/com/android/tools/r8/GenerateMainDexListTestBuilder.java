// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.GenerateMainDexListCommand.Builder;
import com.android.tools.r8.debug.DebugTestConfig;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.InternalOptions;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public class GenerateMainDexListTestBuilder
    extends TestBaseBuilder<
        GenerateMainDexListCommand,
        Builder,
        GenerateMainDexListResult,
        GenerateMainDexListRunResult,
        GenerateMainDexListTestBuilder> {

  public static final Consumer<InternalOptions> DEFAULT_OPTIONS = options -> {};

  private Consumer<InternalOptions> optionsConsumer = DEFAULT_OPTIONS;

  private GenerateMainDexListTestBuilder(TestState state, Builder builder) {
    super(state, builder);
  }

  public static GenerateMainDexListTestBuilder create(TestState state) {
    return new GenerateMainDexListTestBuilder(
        state, GenerateMainDexListCommand.builder(state.getDiagnosticsHandler()));
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
  public GenerateMainDexListRunResult run(TestRuntime runtime, String mainClass, String... args)
      throws IOException, CompilationFailedException {
    throw new Unimplemented("No support for running with a main class");
  }

  @Override
  public GenerateMainDexListTestBuilder addRunClasspathFiles(Collection<Path> files) {
    throw new Unimplemented("No support for run class path");
  }

  @Override
  public GenerateMainDexListTestBuilder addClasspathClasses(Collection<Class<?>> classes) {
    throw new Unimplemented("No support for class path");
  }

  @Override
  public GenerateMainDexListTestBuilder addClasspathFiles(Collection<Path> files) {
    throw new Unimplemented("No support for class path");
  }

  public GenerateMainDexListRunResult run() throws CompilationFailedException, IOException {
    GenerateMainDexListCommand command = builder.build();
    InternalOptions internalOptions = command.getInternalOptions();
    optionsConsumer.accept(internalOptions);
    return new GenerateMainDexListRunResult(
        GenerateMainDexList.runForTesting(command.getInputApp(), internalOptions), getState());
  }

  public GenerateMainDexListTestBuilder addMainDexRules(Collection<String> rules) {
    builder.addMainDexRules(new ArrayList<>(rules), Origin.unknown());
    return self();
  }

  public GenerateMainDexListTestBuilder addMainDexRules(String... rules) {
    return addMainDexRules(Arrays.asList(rules));
  }

  public GenerateMainDexListTestBuilder addDataResources(List<DataEntryResource> resources) {
    resources.forEach(builder.getAppBuilder()::addDataResource);
    return self();
  }

  public GenerateMainDexListTestBuilder addDataEntryResources(DataEntryResource... resources) {
    return addDataResources(Arrays.asList(resources));
  }

  public GenerateMainDexListTestBuilder setMainDexListOutputPath(Path output) {
    builder.setMainDexListOutputPath(output);
    return self();
  }

  public GenerateMainDexListTestBuilder addOptionsModification(
      Consumer<InternalOptions> optionsConsumer) {
    if (optionsConsumer != null) {
      this.optionsConsumer = this.optionsConsumer.andThen(optionsConsumer);
    }
    return self();
  }
}
