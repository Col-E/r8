// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.jasmin.JasminTestBase;
import com.android.tools.r8.utils.AbortException;
import com.android.tools.r8.utils.AndroidApp;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B116282409 extends JasminTestBase {

  private final Backend backend;

  private final boolean enableVerticalClassMerging;

  @Rule public ExpectedException exception = ExpectedException.none();

  @Parameters(name = "Backend: {0}, vertical class merging: {1}")
  public static Collection<Object[]> data() {
    return ImmutableList.of(
        new Object[] {Backend.CF, false},
        new Object[] {Backend.CF, true},
        new Object[] {Backend.DEX, false},
        new Object[] {Backend.DEX, true});
  }

  public B116282409(Backend backend, boolean enableVerticalClassMerging) {
    this.backend = backend;
    this.enableVerticalClassMerging = enableVerticalClassMerging;
  }

  @Test
  public void test() throws Exception {
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

    // Build app.
    if (enableVerticalClassMerging) {
      exception.expect(CompilationFailedException.class);
      exception.expectCause(
          new CustomExceptionMatcher(
              "Unable to rewrite `invoke-direct A.<init>(new B, ...)` in method "
                  + "`void TestClass.main(java.lang.String[])` after type `A` was merged into `B`.",
              "Please add the following rule to your Proguard configuration file: "
                  + "`-keep,allowobfuscation class A`."));
    }

    AndroidApp output =
        compileWithR8(
            jasminBuilder.build(),
            keepMainProguardConfiguration("TestClass"),
            options -> options.enableVerticalClassMerging = enableVerticalClassMerging,
            backend);
    assertFalse(enableVerticalClassMerging);

    // Run app.
    ProcessResult vmResult = runOnVMRaw(output, "TestClass", backend);
    if (backend == Backend.CF) {
      // Verify that the input code does not run with java.
      ProcessResult javaResult = runOnJavaRaw(jasminBuilder, "TestClass");
      assertNotEquals(0, javaResult.exitCode);
      assertThat(javaResult.stderr, containsString("VerifyError"));
      assertThat(javaResult.stderr, containsString("Call to wrong initialization method"));

      // The same should be true for the generated program.
      assertNotEquals(0, vmResult.exitCode);
      assertThat(vmResult.stderr, containsString("VerifyError"));
      assertThat(vmResult.stderr, containsString("Call to wrong initialization method"));
    } else {
      assert backend == Backend.DEX;
      assertEquals(0, vmResult.exitCode);
      assertEquals(String.join(System.lineSeparator(), "In A.<init>()", "42", ""), vmResult.stdout);
    }
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
