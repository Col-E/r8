// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.backports;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticPosition;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.PositionMatcher;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.desugar.backports.AbstractBackportTest.MiniAssert;
import com.android.tools.r8.errors.BackportDiagnostic;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class BackportPlatformTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  static final List<Class<?>> CLASSES =
      ImmutableList.of(MiniAssert.class, TestClass.class, User.class);

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withApiLevel(AndroidApiLevel.J).build();
  }

  public BackportPlatformTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(CLASSES)
        .addKeepMainRule(TestClass.class)
        .addKeepClassAndMembersRules(MiniAssert.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClasses(CLASSES)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test(expected = CompilationFailedException.class)
  public void testPlatformR8() throws Exception {
    testForR8(parameters.getBackend())
        .apply(b -> b.getBuilder().setAndroidPlatformBuild(true))
        .addOptionsModification(o -> o.disableBackportsWithErrorDiagnostics = true)
        .addProgramClasses(CLASSES)
        .addKeepMainRule(TestClass.class)
        .addKeepClassAndMembersRules(MiniAssert.class)
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(this::checkDiagnostics);
  }

  @Test(expected = CompilationFailedException.class)
  public void testPlatformD8() throws Exception {
    testForD8(parameters.getBackend())
        .apply(b -> b.getBuilder().setAndroidPlatformBuild(true))
        .addOptionsModification(o -> o.disableBackportsWithErrorDiagnostics = true)
        .addProgramClasses(CLASSES)
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(this::checkDiagnostics);
  }

  @Test
  public void testPlatformDefinitionD8() throws Exception {
    testForD8(parameters.getBackend())
        .apply(b -> b.getBuilder().setAndroidPlatformBuild(true))
        .addOptionsModification(o -> o.disableBackportsWithErrorDiagnostics = true)
        .addProgramClasses(CLASSES)
        .addProgramClassFileData(
            transformer(BooleanDefinition.class)
                .setClassDescriptor("Ljava/lang/Boolean;")
                .transform())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testPlatformLevelD8() throws Exception {
    assertEquals(AndroidApiLevel.J, parameters.getApiLevel());
    testForD8(parameters.getBackend())
        // Setting platform API will disable any error checking.
        .setMinApi(AndroidApiLevel.ANDROID_PLATFORM_CONSTANT)
        .apply(b -> b.getBuilder().setAndroidPlatformBuild(true))
        .addOptionsModification(o -> o.disableBackportsWithErrorDiagnostics = true)
        .addProgramClasses(CLASSES)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  private void checkDiagnostics(TestDiagnosticMessages diagnostics) {
    diagnostics
        .assertAllErrorsMatch(
            allOf(
                diagnosticType(BackportDiagnostic.class),
                diagnosticMessage(
                    containsString("int java.lang.Boolean.compare(boolean, boolean)")),
                diagnosticPosition(PositionMatcher.positionMethodName("testBooleanCompare"))))
        .assertOnlyErrors();
  }

  // Implementation of java.lang.Boolean.compare to test no error is triggered for
  // compilation units "defining" the backport method.
  public static class BooleanDefinition {
    public int compare(boolean a, boolean b) {
      return (a ? 1 : 0) - (b ? 1 : 0);
    }
  }

  static class User {

    private static void testBooleanCompare() {
      MiniAssert.assertTrue(Boolean.compare(true, false) > 0);
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      User.testBooleanCompare();
      System.out.println("Hello, world");
    }
  }
}
