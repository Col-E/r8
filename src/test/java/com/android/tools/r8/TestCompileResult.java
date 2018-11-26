// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.TestBase.Backend.DEX;

import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.debug.CfDebugTestConfig;
import com.android.tools.r8.debug.DebugTestConfig;
import com.android.tools.r8.debug.DexDebugTestConfig;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.invokesuper.Consumer;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

public abstract class TestCompileResult<RR extends TestRunResult> {
  final TestState state;
  public final AndroidApp app;

  TestCompileResult(TestState state, AndroidApp app) {
    this.state = state;
    this.app = app;
  }

  public abstract Backend getBackend();

  protected abstract RR createRunResult(AndroidApp add, ProcessResult result);

  public RR run(Class<?> mainClass) throws IOException {
    return run(mainClass.getTypeName());
  }

  public RR run(String mainClass) throws IOException {
    switch (getBackend()) {
      case DEX:
        return runArt(mainClass);
      case CF:
        return runJava(mainClass);
      default:
        throw new Unreachable();
    }
  }

  public TestCompileResult writeToZip(Path file) throws IOException {
    app.writeToZip(file, getBackend() == DEX ? OutputMode.DexIndexed : OutputMode.ClassFile);
    return this;
  }

  public CodeInspector inspector() throws IOException, ExecutionException {
    return new CodeInspector(app);
  }

  public TestCompileResult<RR> inspect(Consumer<CodeInspector> consumer)
      throws IOException, ExecutionException {
    consumer.accept(inspector());
    return this;
  }

  public DebugTestConfig debugConfig() {
    // Rethrow exceptions since debug config is usually used in a delayed wrapper which
    // does not declare exceptions.
    try {
      Path out = state.getNewTempFolder().resolve("out.zip");
      switch (getBackend()) {
        case CF:
          {
            app.writeToZip(out, OutputMode.ClassFile);
            return new CfDebugTestConfig().addPaths(out);
          }
        case DEX:
          {
            app.writeToZip(out, OutputMode.DexIndexed);
            return new DexDebugTestConfig().addPaths(out);
          }
        default:
          throw new Unreachable();
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private RR runJava(String mainClass) throws IOException {
    Path out = state.getNewTempFolder().resolve("out.zip");
    app.writeToZip(out, OutputMode.ClassFile);
    ProcessResult result = ToolHelper.runJava(out, mainClass);
    return createRunResult(app, result);
  }

  private RR runArt(String mainClass) throws IOException {
    Path out = state.getNewTempFolder().resolve("out.zip");
    app.writeToZip(out, OutputMode.DexIndexed);
    ProcessResult result = ToolHelper.runArtRaw(out.toString(), mainClass);
    return createRunResult(app, result);
  }

  public TestRunResult runDex2Oat() throws IOException {
    return runDex2Oat(ToolHelper.getDexVm());
  }

  public TestRunResult runDex2Oat(DexVm vm) throws IOException {
    assert getBackend() == DEX;
    Path tmp = state.getNewTempFolder();
    Path jarFile = tmp.resolve("out.jar");
    Path oatFile = tmp.resolve("out.oat");
    app.writeToZip(jarFile, OutputMode.DexIndexed);
    return new TestRunResult(app, ToolHelper.runDex2OatRaw(jarFile, oatFile, vm));
  }
}
