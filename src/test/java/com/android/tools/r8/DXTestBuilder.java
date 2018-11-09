// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.D8Command.Builder;
import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

// The type arguments D8Command, Builder is not relevant for running DX.
public class DXTestBuilder
    extends TestCompilerBuilder<
        D8Command, Builder, DXTestCompileResult, DXTestRunResult, DXTestBuilder> {

  // Ordered list of injar entries.
  private List<Path> injars = new ArrayList<>();

  private DXTestBuilder(TestState state) {
    super(state, D8Command.builder(), Backend.DEX);
  }

  public static DXTestBuilder create(TestState state) {
    return new DXTestBuilder(state);
  }

  @Override
  DXTestBuilder self() {
    return this;
  }

  @Override
  DXTestCompileResult internalCompile(
      Builder builder, Consumer<InternalOptions> optionsConsumer, Supplier<AndroidApp> app)
      throws CompilationFailedException {
    try {
      Path dxOutputFolder = getState().getNewTempFolder();
      Path outJar = dxOutputFolder.resolve("output.jar");

      List<String> args = new ArrayList<>();
      args.add("--output=" + outJar.toString());
      args.addAll(injars.stream().map(Path::toString).collect(Collectors.toList()));
      ProcessResult result = ToolHelper.runDX(args.toArray(new String[0]));
      if (result.exitCode != 0) {
        throw new CompilationFailedException(result.toString());
      }
      return new DXTestCompileResult(
          getState(), AndroidApp.builder().addProgramFile(outJar).build());
    } catch (IOException e) {
      throw new CompilationFailedException(e);
    }
  }

  @Override
  public DXTestBuilder addProgramClasses(Collection<Class<?>> classes) {
    throw new Unimplemented("No support for adding classes directly");
  }

  @Override
  public DXTestBuilder addProgramFiles(Collection<Path> files) {
    injars.addAll(files);
    return self();
  }
}
