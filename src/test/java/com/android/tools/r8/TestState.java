// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.BiFunction;
import org.junit.rules.TemporaryFolder;

public class TestState {

  private final TemporaryFolder temp;
  private final TestDiagnosticMessagesImpl messages;

  private Set<String> mainDexClasses;

  private String stdout;
  private String stderr;

  public TestState(TemporaryFolder temp) {
    this(temp, new TestDiagnosticMessagesImpl());
  }

  public TestState(TemporaryFolder temp, TestDiagnosticMessagesImpl handler) {
    this.temp = temp;
    this.messages = handler;
  }

  public TemporaryFolder getTempFolder() {
    return temp;
  }

  public Path getNewTempFolder() throws IOException {
    return temp.newFolder().toPath();
  }

  public Path getNewTempFile(String name) throws IOException {
    return getNewTempFolder().resolve(name);
  }

  public DiagnosticsHandler getDiagnosticsHandler() {
    return messages;
  }

  public TestDiagnosticMessages getDiagnosticsMessages() {
    return messages;
  }

  public Set<String> getMainDexClasses() {
    return mainDexClasses;
  }

  void setMainDexClasses(Set<String> mainDexClasses) {
    this.mainDexClasses = mainDexClasses;
  }

  public String getStdout() {
    return stdout;
  }

  void setStdout(String stdout) {
    this.stdout = stdout;
  }

  public String getStderr() {
    return stderr;
  }

  void setStderr(String stderr) {
    this.stderr = stderr;
  }

  void setDiagnosticsLevelModifier(
      BiFunction<DiagnosticsLevel, Diagnostic, DiagnosticsLevel> modifier) {
    messages.setDiagnosticsLevelModifier(modifier);
  }
}
