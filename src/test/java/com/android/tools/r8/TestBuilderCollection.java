// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.debug.DebugTestConfig;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.utils.Pair;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/** Abstraction to allow setup and execution of multiple test builders. */
public class TestBuilderCollection
    extends TestBuilder<TestRunResultCollection, TestBuilderCollection> {

  public static TestBuilderCollection create(
      TestState state,
      List<Pair<String, TestBuilder<? extends TestRunResult<?>, ?>>> testBuilders) {
    return new TestBuilderCollection(state, testBuilders);
  }

  private final List<Pair<String, TestBuilder<? extends TestRunResult<?>, ?>>> builders;

  private TestBuilderCollection(
      TestState state, List<Pair<String, TestBuilder<? extends TestRunResult<?>, ?>>> builders) {
    super(state);
    assert !builders.isEmpty();
    this.builders = builders;
  }

  @Override
  TestBuilderCollection self() {
    return this;
  }

  @Override
  public TestRunResultCollection run(TestRuntime runtime, String mainClass, String... args)
      throws CompilationFailedException, ExecutionException, IOException {
    List<Pair<String, TestRunResult<?>>> runs = new ArrayList<>(builders.size());
    for (Pair<String, TestBuilder<? extends TestRunResult<?>, ?>> builder : builders) {
      runs.add(new Pair<>(builder.getFirst(), builder.getSecond().run(runtime, mainClass, args)));
    }
    return TestRunResultCollection.create(runs);
  }

  private TestBuilderCollection forEach(Consumer<TestBuilder<? extends TestRunResult<?>, ?>> fn) {
    builders.forEach(b -> fn.accept(b.getSecond()));
    return self();
  }

  @Override
  public DebugTestConfig debugConfig() {
    throw new Unimplemented("Unsupported debug config as of now...");
  }

  @Override
  public TestBuilderCollection addProgramFiles(Collection<Path> files) {
    return forEach(b -> b.addProgramFiles(files));
  }

  @Override
  public TestBuilderCollection addProgramClassFileData(Collection<byte[]> classes) {
    return forEach(b -> b.addProgramClassFileData(classes));
  }

  @Override
  public TestBuilderCollection addProgramDexFileData(Collection<byte[]> data) {
    return forEach(b -> b.addProgramDexFileData(data));
  }

  @Override
  public TestBuilderCollection addLibraryFiles(Collection<Path> files) {
    return forEach(b -> b.addLibraryFiles(files));
  }

  @Override
  public TestBuilderCollection addLibraryClasses(Collection<Class<?>> classes) {
    return forEach(b -> b.addLibraryClasses(classes));
  }

  @Override
  public TestBuilderCollection addClasspathClasses(Collection<Class<?>> classes) {
    return forEach(b -> b.addClasspathClasses(classes));
  }

  @Override
  public TestBuilderCollection addClasspathFiles(Collection<Path> files) {
    return forEach(b -> b.addClasspathFiles(files));
  }

  @Override
  public TestBuilderCollection addRunClasspathFiles(Collection<Path> files) {
    return forEach(b -> b.addRunClasspathFiles(files));
  }
}
