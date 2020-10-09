// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.checkdiscarded;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.NoneRuntime;
import com.android.tools.r8.checkdiscarded.testclasses.Main;
import com.android.tools.r8.checkdiscarded.testclasses.UnusedClass;
import com.android.tools.r8.checkdiscarded.testclasses.UsedClass;
import com.android.tools.r8.checkdiscarded.testclasses.WillBeGone;
import com.android.tools.r8.checkdiscarded.testclasses.WillStay;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.InternalOptions;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CheckDiscardedTest extends TestBase {

  @Parameters(name = "{0}, minify:{1}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withNoneRuntime().build(), BooleanUtils.values());
  }

  public final boolean minify;

  public CheckDiscardedTest(TestParameters parameters, boolean minify) {
    assertEquals(NoneRuntime.getInstance(), parameters.getRuntime());
    this.minify = minify;
  }

  private void compile(
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
              .minification(minify)
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
    compile(WillBeGone.class, false, null);
  }

  @Test
  public void classesAreNotGone() {
    Consumer<TestDiagnosticMessages> check =
        diagnostics ->
            diagnostics
                .assertNoInfos()
                .assertNoWarnings()
                .assertErrorsMatch(
                    diagnosticMessage(
                        allOf(
                            containsString("Discard checks failed"),
                            containsString("UsedClass was not discarded"),
                            containsString("is instantiated in"),
                            containsString("Main was not discarded"),
                            containsString("is referenced in keep rule"))));
    compile(WillStay.class, false, check);
  }

  @Test
  public void membersAreGone() {
    compile(WillBeGone.class, true, null);
  }

  @Test
  public void membersAreNotGone() {
    Consumer<TestDiagnosticMessages> check =
        diagnostics ->
            diagnostics
                .assertNoInfos()
                .assertNoWarnings()
                .assertErrorsMatch(
                    diagnosticMessage(
                        allOf(
                            containsString("Discard checks failed"),
                            containsString("was not discarded"),
                            containsString("is invoked from"))));
    compile(WillStay.class, true, check);
  }

}
