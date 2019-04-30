// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.checkdiscarded;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.checkdiscarded.testclasses.Main;
import com.android.tools.r8.checkdiscarded.testclasses.UnusedClass;
import com.android.tools.r8.checkdiscarded.testclasses.UsedClass;
import com.android.tools.r8.checkdiscarded.testclasses.WillBeGone;
import com.android.tools.r8.checkdiscarded.testclasses.WillStay;
import com.android.tools.r8.utils.InternalOptions;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CheckDiscardedTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private final TestParameters parameters;

  public CheckDiscardedTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private void compile(
      boolean obfuscate,
      Class annotation,
      boolean checkMembers,
      Consumer<TestDiagnosticMessages> onCompilationFailure) {
    R8FullTestBuilder builder = testForR8(Backend.DEX);
    TestDiagnosticMessages diagnostics = builder.getState().getDiagnosticsMessages();
    try {
      R8TestCompileResult result =
          builder
              .addProgramClasses(UnusedClass.class, UsedClass.class, Main.class)
              .addKeepMainRule(Main.class)
              .addKeepRules(checkDiscardRule(checkMembers, annotation))
              .minification(obfuscate)
              .addOptionsModification(this::noInlining)
              .compile();
      assertNull(onCompilationFailure);
      result.assertNoMessages();
    } catch (CompilationFailedException e) {
      onCompilationFailure.accept(diagnostics);
    }
  }

  private void noInlining(InternalOptions options) {
    options.enableInlining = false;
  }

  private String checkDiscardRule(boolean member, Class annotation) {
    if (member) {
      return "-checkdiscard class * { @" + annotation.getName() + " *; }";
    } else {
      return "-checkdiscard @" + annotation.getName() + " class *";
    }
  }

  @Test
  public void classesAreGone() {
    compile(false, WillBeGone.class, false, null);
    compile(true, WillBeGone.class, false, null);
  }

  @Test
  public void classesAreNotGone() {
    Consumer<TestDiagnosticMessages> check =
        diagnostics -> {
          List<Diagnostic> infos = diagnostics.getInfos();
          assertEquals(2, infos.size());
          String messageUsedClass = infos.get(1).getDiagnosticMessage();
          assertThat(messageUsedClass, containsString("UsedClass was not discarded"));
          assertThat(messageUsedClass, containsString("is instantiated in"));
          String messageMain = infos.get(0).getDiagnosticMessage();
          assertThat(messageMain, containsString("Main was not discarded"));
          assertThat(messageMain, containsString("is referenced in keep rule"));
        };
    compile(false, WillStay.class, false, check);
    compile(true, WillStay.class, false, check);
  }

  @Test
  public void membersAreGone() {
    compile(false, WillBeGone.class, true, null);
    compile(true, WillBeGone.class, true, null);
  }

  @Test
  public void membersAreNotGone() {
    Consumer<TestDiagnosticMessages> check =
        diagnostics -> {
          List<Diagnostic> infos = diagnostics.getInfos();
          assertEquals(1, infos.size());
          String message = infos.get(0).getDiagnosticMessage();
          assertThat(message, containsString("was not discarded"));
          assertThat(message, containsString("is invoked from"));
        };
    compile(false, WillStay.class, true, check);
    compile(true, WillStay.class, true, check);
  }

}
