// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.TestBase.Backend.DEX;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

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
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import org.hamcrest.Matcher;

public abstract class TestCompileResult<
    CR extends TestCompileResult<CR, RR>, RR extends TestRunResult> {

  final TestState state;
  public final AndroidApp app;

  TestCompileResult(TestState state, AndroidApp app) {
    this.state = state;
    this.app = app;
  }

  public abstract CR self();

  public abstract Backend getBackend();

  public abstract TestDiagnosticMessages getDiagnosticMessages();

  protected abstract RR createRunResult(ProcessResult result);

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

  public CR writeToZip(Path file) throws IOException {
    app.writeToZip(file, getBackend() == DEX ? OutputMode.DexIndexed : OutputMode.ClassFile);
    return self();
  }

  public CodeInspector inspector() throws IOException, ExecutionException {
    return new CodeInspector(app);
  }

  public CR inspect(Consumer<CodeInspector> consumer) throws IOException, ExecutionException {
    consumer.accept(inspector());
    return self();
  }

  public CR assertNoMessages() {
    assertEquals(0, getDiagnosticMessages().getInfos().size());
    assertEquals(0, getDiagnosticMessages().getWarnings().size());
    assertEquals(0, getDiagnosticMessages().getErrors().size());
    return self();
  }

  public CR assertOnlyInfos() {
    assertNotEquals(0, getDiagnosticMessages().getInfos().size());
    assertEquals(0, getDiagnosticMessages().getWarnings().size());
    assertEquals(0, getDiagnosticMessages().getErrors().size());
    return self();
  }

  public CR assertOnlyWarnings() {
    assertEquals(0, getDiagnosticMessages().getInfos().size());
    assertNotEquals(0, getDiagnosticMessages().getWarnings().size());
    assertEquals(0, getDiagnosticMessages().getErrors().size());
    return self();
  }

  public CR assertWarningMessageThatMatches(Matcher<String> matcher) {
    assertNotEquals(0, getDiagnosticMessages().getWarnings().size());
    for (int i = 0; i < getDiagnosticMessages().getWarnings().size(); i++) {
      if (matcher.matches(getDiagnosticMessages().getWarnings().get(i).getDiagnosticMessage())) {
        return self();
      }
    }
    StringBuilder builder = new StringBuilder("No warning matches " + matcher.toString());
    builder.append(System.lineSeparator());
    if (getDiagnosticMessages().getWarnings().size() == 0) {
      builder.append("There where no warnings.");
    } else {
      builder.append("There where " + getDiagnosticMessages().getWarnings().size() + " warnings:");
      builder.append(System.lineSeparator());
      for (int i = 0; i < getDiagnosticMessages().getWarnings().size(); i++) {
        builder.append(getDiagnosticMessages().getWarnings().get(i).getDiagnosticMessage());
        builder.append(System.lineSeparator());
      }
    }
    fail(builder.toString());
    return self();
  }

  public CR assertNoWarningMessageThatMatches(Matcher<String> matcher) {
    assertNotEquals(0, getDiagnosticMessages().getWarnings().size());
    for (int i = 0; i < getDiagnosticMessages().getWarnings().size(); i++) {
      String message = getDiagnosticMessages().getWarnings().get(i).getDiagnosticMessage();
      if (matcher.matches(message)) {
        fail("The warning: \"" + message + "\" + matches " + matcher + ".");
      }
    }
    return self();
  }

  public CR disassemble(PrintStream ps) throws IOException, ExecutionException {
    ToolHelper.disassemble(app, ps);
    return self();
  }

  public CR disassemble() throws IOException, ExecutionException {
    return disassemble(System.out);
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
    return createRunResult(result);
  }

  private RR runArt(String mainClass) throws IOException {
    Path out = state.getNewTempFolder().resolve("out.zip");
    app.writeToZip(out, OutputMode.DexIndexed);
    ProcessResult result = ToolHelper.runArtRaw(out.toString(), mainClass);
    return createRunResult(result);
  }

  public Dex2OatTestRunResult runDex2Oat() throws IOException {
    return runDex2Oat(ToolHelper.getDexVm());
  }

  public Dex2OatTestRunResult runDex2Oat(DexVm vm) throws IOException {
    assert getBackend() == DEX;
    Path tmp = state.getNewTempFolder();
    Path jarFile = tmp.resolve("out.jar");
    Path oatFile = tmp.resolve("out.oat");
    app.writeToZip(jarFile, OutputMode.DexIndexed);
    return new Dex2OatTestRunResult(app, ToolHelper.runDex2OatRaw(jarFile, oatFile, vm));
  }
}
