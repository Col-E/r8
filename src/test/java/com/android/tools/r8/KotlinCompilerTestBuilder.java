// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.R8Command.Builder;
import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class KotlinCompilerTestBuilder
    extends TestCompilerBuilder<
        R8Command,
        Builder,
        KotlinTestCompileResult,
        KotlinTestRunResult,
        KotlinCompilerTestBuilder> {

  private List<Path> ktPaths;
  private List<Path> classPaths;

  private KotlinCompilerTestBuilder(TestState state, Builder builder) {
    super(state, builder, Backend.CF);
  }

  public static KotlinCompilerTestBuilder create(TestState state) {
    return new KotlinCompilerTestBuilder(state, R8Command.builder());
  }

  @Override
  KotlinCompilerTestBuilder self() {
    return this;
  }

  @Override
  KotlinTestCompileResult internalCompile(
      Builder builder,
      Consumer<InternalOptions> optionsConsumer,
      Supplier<AndroidApp> app)
      throws CompilationFailedException {
    try {
      Path outputFolder = getState().getNewTempFolder();
      Path outputJar = outputFolder.resolve("output.jar");
      ProcessResult processResult =
          ToolHelper.runKotlinc(
              null,
              classPaths,
              outputJar,
              null, // extra options
              ktPaths == null ? ToolHelper.EMPTY_PATH : ktPaths.toArray(ToolHelper.EMPTY_PATH)
          );
      return new KotlinTestCompileResult(getState(), outputJar, processResult);
    } catch (IOException e) {
      throw new CompilationFailedException(e);
    }
  }

  @Override
  public KotlinCompilerTestBuilder addClasspathClasses(Collection<Class<?>> classes) {
    throw new Unimplemented("No support for adding classpath classes directly");
  }

  @Override
  public KotlinCompilerTestBuilder addClasspathFiles(Collection<Path> files) {
    if (classPaths == null) {
      classPaths = new ArrayList<>();
    }
    classPaths.addAll(files);
    return self();
  }

  public KotlinCompilerTestBuilder addSourceFiles(Path... files) {
    if (ktPaths == null) {
      ktPaths = new ArrayList<>();
    }
    ktPaths.addAll(Arrays.asList(files));
    return self();
  }

}
