// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.core.IsNot.not;

import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.jasmin.JasminTestBase;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Collection;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvalidTypesTest extends JasminTestBase {

  private enum Mode {
    NO_INVOKE,
    INVOKE_UNVERIFIABLE_METHOD,
    INVOKE_VERIFIABLE_METHOD_ON_UNVERIFIABLE_CLASS;

    public String instruction() {
      switch (this) {
        case NO_INVOKE:
          return "";

        case INVOKE_UNVERIFIABLE_METHOD:
          return "invokestatic UnverifiableClass/unverifiableMethod()V";

        case INVOKE_VERIFIABLE_METHOD_ON_UNVERIFIABLE_CLASS:
          return "invokestatic UnverifiableClass/verifiableMethod()V";

        default:
          throw new Unreachable();
      }
    }
  }

  private final Backend backend;
  private final Mode mode;

  public InvalidTypesTest(Backend backend, Mode mode) {
    this.backend = backend;
    this.mode = mode;
  }

  @Parameters(name = "Backend: {0}, mode: {1}")
  public static Collection<Object[]> parameters() {
    return buildParameters(Backend.values(), Mode.values());
  }

  @Test
  public void test() throws Exception {
    JasminBuilder jasminBuilder = new JasminBuilder();

    ClassBuilder classA = jasminBuilder.addClass("A");
    classA.addDefaultConstructor();

    ClassBuilder classB = jasminBuilder.addClass("B");
    classB.addDefaultConstructor();

    ClassBuilder mainClass = jasminBuilder.addClass("TestClass");
    mainClass.addStaticField("f", "LA;");
    mainClass.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        // Print "Hello!".
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "ldc \"Hello!\"",
        "invokevirtual java/io/PrintStream/println(Ljava/lang/String;)V",
        // Invoke method on UnverifiableClass, depending on the mode.
        mode.instruction(),
        // Print "Goodbye!".
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "ldc \"Goodbye!\"",
        "invokevirtual java/io/PrintStream/println(Ljava/lang/String;)V",
        "return");

    mainClass
        .staticMethodBuilder("m", ImmutableList.of(), "V")
        .setCode(
            "getstatic java/lang/System/out Ljava/io/PrintStream;",
            "getstatic TestClass/f LA;",
            "invokevirtual java/lang/Object/toString()Ljava/lang/String;",
            "invokevirtual java/io/PrintStream/println(Ljava/lang/String;)V",
            "return")
        .build();

    ClassBuilder UnverifiableClass = jasminBuilder.addClass("UnverifiableClass");
    UnverifiableClass.staticMethodBuilder("unverifiableMethod", ImmutableList.of(), "V")
        .setCode(
            "new A",
            "dup",
            "invokespecial A/<init>()V",
            "putstatic TestClass/f LA;",
            "invokestatic TestClass/m()V",
            "new B",
            "dup",
            "invokespecial B/<init>()V",
            "putstatic TestClass/f LA;",
            "invokestatic TestClass/m()V",
            "return")
        .build();
    UnverifiableClass.staticMethodBuilder("verifiableMethod", ImmutableList.of(), "V")
        .setCode(
            "getstatic java/lang/System/out Ljava/io/PrintStream;",
            "ldc \"In verifiable method!\"",
            "invokevirtual java/io/PrintStream/println(Ljava/lang/String;)V",
            "return")
        .build();

    Path inputJar = temp.getRoot().toPath().resolve("input.jar");
    jasminBuilder.writeJar(inputJar);

    if (backend == Backend.CF) {
      TestRunResult jvmResult = testForJvm().addClasspath(inputJar).run(mainClass.name);
      checkTestRunResult(jvmResult, false);
    } else {
      assert backend == Backend.DEX;

      TestRunResult dxResult = testForDX().addProgramFiles(inputJar).run(mainClass.name);
      checkTestRunResult(dxResult, false);

      TestRunResult d8Result = testForD8().addProgramFiles(inputJar).run(mainClass.name);
      checkTestRunResult(d8Result, false);
    }

    TestRunResult r8Result =
        testForR8(backend)
            .addProgramFiles(inputJar)
            .addKeepMainRule(mainClass.name)
            .addOptionsModification(options -> options.testing.allowTypeErrors = true)
            .run(mainClass.name);
    checkTestRunResult(r8Result, true);
  }

  private void checkTestRunResult(TestRunResult result, boolean isR8) {
    switch (mode) {
      case NO_INVOKE:
        result.assertSuccessWithOutput(getExpectedOutput(isR8));
        break;

      case INVOKE_VERIFIABLE_METHOD_ON_UNVERIFIABLE_CLASS:
        if (isR8) {
          result.assertSuccessWithOutput(getExpectedOutput(isR8));
        } else {
          result
              .assertFailureWithOutput(getExpectedOutput(isR8))
              .assertFailureWithErrorThatMatches(getMatcherForExpectedError());
        }
        break;

      case INVOKE_UNVERIFIABLE_METHOD:
        if (isR8) {
          result
              .assertFailureWithOutput(getExpectedOutput(isR8))
              .assertFailureWithErrorThatMatches(
                  allOf(
                      containsString("java.lang.NullPointerException"),
                      not(containsString("java.lang.VerifyError"))));
        } else {
          result
              .assertFailureWithOutput(getExpectedOutput(isR8))
              .assertFailureWithErrorThatMatches(getMatcherForExpectedError());
        }
        break;

      default:
        throw new Unreachable();
    }
  }

  private String getExpectedOutput(boolean isR8) {
    if (mode == Mode.NO_INVOKE) {
      return StringUtils.joinLines("Hello!", "Goodbye!", "");
    }
    if (isR8 && mode == Mode.INVOKE_VERIFIABLE_METHOD_ON_UNVERIFIABLE_CLASS) {
      return StringUtils.joinLines("Hello!", "In verifiable method!", "Goodbye!", "");
    }
    return StringUtils.joinLines("Hello!", "");
  }

  private Matcher<String> getMatcherForExpectedError() {
    if (backend == Backend.CF) {
      return allOf(
          containsString("java.lang.VerifyError"),
          containsString("Bad type in putfield/putstatic"));
    }

    assert backend == Backend.DEX;
    return allOf(
        containsString("java.lang.VerifyError"),
        anyOf(
            containsString("register v0 has type Precise Reference: B but expected Reference: A"),
            containsString("VFY: storing type 'LB;' into field type 'LA;'")));
  }
}
