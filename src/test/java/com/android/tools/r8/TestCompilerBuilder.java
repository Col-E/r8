// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidAppConsumers;
import com.android.tools.r8.utils.InternalOptions;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.function.Consumer;

public abstract class TestCompilerBuilder<
        C extends BaseCompilerCommand,
        B extends BaseCompilerCommand.Builder<C, B>,
        T extends TestCompilerBuilder<C, B, T>>
    extends TestBuilder<T> {

  public static final Consumer<InternalOptions> DEFAULT_OPTIONS =
      new Consumer<InternalOptions>() {
        @Override
        public void accept(InternalOptions options) {}
      };

  private final B builder;
  private final Backend backend;

  // Default initialized setup. Can be overwritten if needed.
  private Path defaultLibrary;
  private ProgramConsumer programConsumer;
  private AndroidApiLevel defaultMinApiLevel = ToolHelper.getMinApiLevelForDexVm();
  private Consumer<InternalOptions> optionsConsumer = DEFAULT_OPTIONS;

  TestCompilerBuilder(TestState state, B builder, Backend backend) {
    super(state);
    this.builder = builder;
    this.backend = backend;
    defaultLibrary = TestBase.runtimeJar(backend);
    programConsumer = TestBase.emptyConsumer(backend);
  }

  abstract T self();

  abstract void internalCompile(B builder, Consumer<InternalOptions> optionsConsumer)
      throws CompilationFailedException;

  public T addOptionsModification(Consumer<InternalOptions> optionsConsumer) {
    this.optionsConsumer = this.optionsConsumer.andThen(optionsConsumer);
    return self();
  }

  public TestCompileResult compile() throws CompilationFailedException {
    AndroidAppConsumers sink = new AndroidAppConsumers();
    builder.setProgramConsumer(sink.wrapProgramConsumer(programConsumer));
    if (defaultLibrary != null) {
      builder.addLibraryFiles(defaultLibrary);
    }
    if (backend == Backend.DEX && defaultMinApiLevel != null) {
      builder.setMinApiLevel(defaultMinApiLevel.getLevel());
    }
    internalCompile(builder, optionsConsumer);
    return new TestCompileResult(getState(), backend, sink.build());
  }

  @Override
  public TestRunResult run(String mainClass) throws IOException, CompilationFailedException {
    return compile().run(mainClass);
  }

  public T setMode(CompilationMode mode) {
    builder.setMode(mode);
    return self();
  }

  public T debug() {
    return setMode(CompilationMode.DEBUG);
  }

  public T release() {
    return setMode(CompilationMode.RELEASE);
  }

  public T setMinApi(AndroidApiLevel minApiLevel) {
    // Should we ignore min-api calls when backend == CF?
    this.defaultMinApiLevel = null;
    builder.setMinApiLevel(minApiLevel.getLevel());
    return self();
  }

  public T setProgramConsumer(ProgramConsumer programConsumer) {
    assert programConsumer != null;
    this.programConsumer = programConsumer;
    return self();
  }

  @Override
  public T addProgramFiles(Collection<Path> files) {
    builder.addProgramFiles(files);
    return self();
  }

  @Override
  public T addLibraryFiles(Collection<Path> files) {
    defaultLibrary = null;
    builder.addLibraryFiles(files);
    return self();
  }
}
