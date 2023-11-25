// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.jasmin.JasminTestBase;
import com.android.tools.r8.utils.AbortException;
import com.android.tools.r8.utils.BooleanUtils;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B116282409 extends JasminTestBase {

  private static List<byte[]> programClassFileData;

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean enableVerticalClassMerging;

  @Rule public ExpectedException exception = ExpectedException.none();

  @Parameters(name = "{0}, vertical class merging: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  @BeforeClass
  public static void setup() throws Exception {
    JasminBuilder jasminBuilder = new JasminBuilder();

    // Create a class A with a default constructor that prints "In A.<init>()".
    ClassBuilder classBuilder = jasminBuilder.addClass("A");
    classBuilder.addMethod(
        "public",
        "<init>",
        ImmutableList.of(),
        "V",
        ".limit stack 2",
        ".limit locals 1",
        "aload_0",
        "invokespecial java/lang/Object/<init>()V",
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "ldc \"In A.<init>()\"",
        "invokevirtual java/io/PrintStream/println(Ljava/lang/String;)V",
        "return");

    // Create a class B that inherits from A.
    classBuilder = jasminBuilder.addClass("B", "A");

    // Also add a simple method that the class inliner would inline.
    classBuilder.addVirtualMethod(
        "m", "I", ".limit stack 5", ".limit locals 5", "bipush 42", "ireturn");

    // Add a test class that initializes an instance of B using A.<init>.
    classBuilder = jasminBuilder.addClass("TestClass");
    classBuilder.addMainMethod(
        ".limit stack 5",
        ".limit locals 5",
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "new B",
        "dup",
        "invokespecial A/<init>()V", // NOTE: usually B.<init>
        "invokevirtual B/m()I",
        "invokevirtual java/io/PrintStream/println(I)V",
        "return");

    programClassFileData = jasminBuilder.buildClasses();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClassFileData(programClassFileData)
        .run(parameters.getRuntime(), "TestClass")
        .assertFailureWithErrorThatThrows(VerifyError.class)
        .assertFailureWithErrorThatMatches(containsString("Call to wrong initialization method"));
  }

  @Test
  public void testR8() throws Exception {
    if (enableVerticalClassMerging) {
      exception.expect(CompilationFailedException.class);
      exception.expectCause(
          new CustomExceptionMatcher(
              "Unable to rewrite `invoke-direct A.<init>(new B, ...)` in method "
                  + "`void TestClass.main(java.lang.String[])` after type `A` was merged into `B`.",
              "Please add the following rule to your Proguard configuration file: "
                  + "`-keep,allowobfuscation class A`."));
    }

    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramClassFileData(programClassFileData)
            .addKeepMainRule("TestClass")
            .addOptionsModification(
                options ->
                    options.getVerticalClassMergerOptions().setEnabled(enableVerticalClassMerging))
            .allowDiagnosticWarningMessages()
            .setMinApi(parameters)
            .compile();

    assertFalse(enableVerticalClassMerging);

    compileResult
        .run(parameters.getRuntime(), "TestClass")
        .applyIf(
            parameters.isCfRuntime(),
            runResult ->
                runResult
                    .assertFailureWithErrorThatThrows(VerifyError.class)
                    .assertFailureWithErrorThatMatches(
                        containsString("Call to wrong initialization method")),
            runResult -> {
              if (parameters.getDexRuntimeVersion().isDalvik()) {
                runResult.assertFailureWithErrorThatMatches(
                    containsString(
                        "VFY: invoke-direct <init> on super only allowed for 'this' in <init>"));
              } else {
                runResult.assertSuccessWithOutputLines("In A.<init>()", "42");
              }
            });
  }

  private static class CustomExceptionMatcher extends BaseMatcher<Throwable> {

    private List<String> messages;

    public CustomExceptionMatcher(String... messages) {
      this.messages = Arrays.asList(messages);
    }

    @Override
    public void describeTo(Description description) {
      description
          .appendText("a string containing ")
          .appendText(
              String.join(
                  ", ", messages.stream().map(m -> "\"" + m + "\"").collect(Collectors.toList())));
    }

    @Override
    public boolean matches(Object o) {
      if (o instanceof AbortException) {
        AbortException exception = (AbortException) o;
        if (exception.getMessage() != null
            && messages.stream().allMatch(message -> exception.getMessage().contains(message))) {
          return true;
        }
        if (exception.getCause() != null) {
          return matches(exception.getCause());
        }
      }
      return false;
    }
  }
}
