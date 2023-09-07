// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.startup;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.errors.StartupClassesNonStartupFractionDiagnostic;
import com.android.tools.r8.startup.profile.ExternalStartupItem;
import com.android.tools.r8.startup.utils.StartupTestingUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MinimalStartupDexTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimes()
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.L)
        .build();
  }

  @Test
  public void test() throws Exception {
    Set<ExternalStartupItem> startupList = new LinkedHashSet<>();
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .apply(
            StartupTestingUtils.enableStartupInstrumentationForOriginalAppUsingLogcat(parameters))
        .release()
        .setMinApi(parameters)
        .compile()
        .addRunClasspathFiles(StartupTestingUtils.getAndroidUtilLog(temp))
        .run(parameters.getRuntime(), Main.class)
        .apply(StartupTestingUtils.removeStartupListFromStdout(startupList::add))
        .assertSuccessWithOutputLines(getExpectedOutput());

    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepClassAndMembersRules(Main.class)
        .addOptionsModification(
            options ->
                options
                    .getStartupOptions()
                    .setEnableMinimalStartupDex(true)
                    .setEnableStartupCompletenessCheckForTesting())
        .allowDiagnosticInfoMessages()
        .enableInliningAnnotations()
        .apply(testBuilder -> StartupTestingUtils.addStartupProfile(testBuilder, startupList))
        .setMinApi(parameters)
        .compile()
        .inspectDiagnosticMessages(
            diagnostics ->
                diagnostics.assertInfosMatch(
                    diagnosticType(StartupClassesNonStartupFractionDiagnostic.class)))
        .inspectMultiDex(
            primaryDexInspector -> {
              // Main should be in the primary dex.
              ClassSubject mainClassSubject = primaryDexInspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());

              MethodSubject mainMethodSubject = mainClassSubject.mainMethod();
              assertThat(mainMethodSubject, isPresent());
              assertTrue(
                  mainMethodSubject.streamInstructions().noneMatch(InstructionSubject::isThrow));

              MethodSubject onClickMethodSubject =
                  mainClassSubject.uniqueMethodWithOriginalName("onClick");
              assertThat(onClickMethodSubject, isPresent());
              assertTrue(
                  onClickMethodSubject.streamInstructions().anyMatch(InstructionSubject::isThrow));

              // StartupClass should be in the primary dex.
              ClassSubject startupClassSubject = primaryDexInspector.clazz(AStartupClass.class);
              assertThat(startupClassSubject, isPresent());

              MethodSubject startupMethodSubject =
                  startupClassSubject.uniqueMethodWithOriginalName("foo");
              assertThat(startupMethodSubject, isPresent());
              assertTrue(
                  startupMethodSubject.streamInstructions().noneMatch(InstructionSubject::isThrow));
            },
            secondaryDexInspector -> {
              // NonStartupClass should be in the secondary dex and should be transformed such that
              // all methods throw null.
              ClassSubject nonStartupClassSubject =
                  secondaryDexInspector.clazz(NonStartupClass.class);
              assertThat(nonStartupClassSubject, isPresent());

              MethodSubject nonStartupClinitSubject = nonStartupClassSubject.clinit();
              assertThat(nonStartupClinitSubject, isPresent());
              assertTrue(
                  nonStartupClinitSubject
                      .streamInstructions()
                      .anyMatch(InstructionSubject::isThrow));

              MethodSubject nonStartupMethodSubject =
                  nonStartupClassSubject.uniqueMethodWithOriginalName("bar");
              assertThat(nonStartupMethodSubject, isPresent());
              assertTrue(
                  nonStartupMethodSubject
                      .streamInstructions()
                      .anyMatch(InstructionSubject::isThrow));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(getExpectedOutput());
  }

  private List<String> getExpectedOutput() {
    return ImmutableList.of("foo");
  }

  static class Main {

    public static void main(String[] args) {
      AStartupClass.foo();
    }

    // @Keep
    public void onClick() {
      NonStartupClass.bar();
    }
  }

  static class AStartupClass {

    @NeverInline
    static void foo() {
      System.out.println("foo");
    }
  }

  static class NonStartupClass {

    @NeverInline
    static void bar() {
      System.out.println("bar");
    }
  }
}
