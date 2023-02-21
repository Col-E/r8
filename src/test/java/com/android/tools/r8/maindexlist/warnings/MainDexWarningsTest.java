// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.maindexlist.warnings;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MainDexWarningsTest extends TestBase {

  private static final List<Class<?>> testClasses =
      ImmutableList.of(Main.class, Static.class, Static2.class);
  private static final List<Class<?>> testClassesWithoutStatic =
      ImmutableList.of(Main.class, Static2.class);
  private static final Class<?> mainClass = Main.class;

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimes()
        .withApiLevelsEndingAtExcluding(apiLevelWithNativeMultiDexSupport())
        .build();
  }

  public MainDexWarningsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private void classStaticGone(CodeInspector inspector) {
    assertThat(inspector.clazz(Static.class), isAbsent());
    assertThat(inspector.clazz(Static2.class), isAbsent());
  }

  @Test
  public void testNoWarningFromMainDexRules() throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(AndroidApiLevel.K)
        .addProgramClasses(testClasses)
        .addKeepMainRule(mainClass)
        // Include main dex rule for class Static.
        .addMainDexKeepClassRules(Main.class, Static.class)
        .setMinApi(parameters)
        .compile()
        .inspect(this::classStaticGone)
        .assertNoMessages();
  }

  @Test
  public void testWarningFromManualMainDexList() throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(AndroidApiLevel.K)
        .addProgramClasses(testClassesWithoutStatic)
        .addKeepMainRule(mainClass)
        .addDontWarn(Static.class)
        // Include explicit main dex entry for class Static.
        .addMainDexListClasses(Static.class)
        .allowDiagnosticWarningMessages()
        .setMinApi(parameters)
        .compile()
        .inspect(this::classStaticGone)
        .assertOnlyWarnings()
        .assertWarningMessageThatMatches(containsString("Application does not contain"))
        .assertWarningMessageThatMatches(containsString(Static.class.getTypeName()))
        .assertWarningMessageThatMatches(containsString("as referenced in main-dex-list"));
  }

  @Test
  public void testWarningFromManualMainDexListWithRuleAsWell() throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(AndroidApiLevel.K)
        .addProgramClasses(testClassesWithoutStatic)
        .addKeepMainRule(mainClass)
        // Include explicit main dex entry for class Static.
        .addMainDexListClasses(Main.class, Static.class)
        // Include main dex rule for class Static2.
        .addMainDexKeepClassRules(Static2.class)
        .addDontWarn(Static.class)
        .allowDiagnosticWarningMessages()
        .setMinApi(parameters)
        .compile()
        .inspect(this::classStaticGone)
        .assertOnlyWarnings()
        .assertWarningMessageThatMatches(containsString("Application does not contain"))
        .assertWarningMessageThatMatches(containsString(Static.class.getTypeName()))
        .assertWarningMessageThatMatches(containsString("as referenced in main-dex-list"))
        .assertNoWarningMessageThatMatches(containsString(Static2.class.getTypeName()));
  }
}

class Main {
  public static void main(String[] args) {
    System.out.println(Static.m());
    System.out.println(Static2.m());
  }
}

class Static {
  public static int m() {
    return 1;
  }
}

class Static2 {
  public static int m() {
    return 1;
  }
}
