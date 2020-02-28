// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.OutputMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DesugarToClassFile extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public DesugarToClassFile(TestParameters parameters) {
    this.parameters = parameters;
  }

  private void checkSomething(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());
  }

  private void checkDiagnostics(TestDiagnosticMessages messages) {
    messages.assertOnlyWarnings();
    messages.assertWarningsCount(1);
    assertThat(
        messages.getWarnings().get(0).getDiagnosticMessage(),
        containsString("not officially supported"));
  }

  @Test
  public void test() throws Exception {
    // Use D8 to desugar with Java classfile output.
    Path jar =
        testForD8()
            .addInnerClasses(DesugarToClassFile.class)
            .setMinApi(parameters.getApiLevel())
            .setOutputMode(OutputMode.ClassFile)
            .compile()
            .inspectDiagnosticMessages(this::checkDiagnostics)
            .inspect(this::checkSomething)
            .writeToZip();

    if (parameters.getRuntime().isCf()) {
      // Run on the JVM
      testForJvm()
          .addProgramFiles(jar)
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutputLines("Hello, world!", "I::foo");
    } else {
      assert parameters.getRuntime().isDex();
      // Convert to DEX without desugaring.
      testForD8()
          .addProgramFiles(jar)
          .setEnableDesugaring(false)
          .setMinApi(parameters.getApiLevel())
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutputLines("Hello, world!", "I::foo");
    }
  }

  interface I {
    default void foo() {
      System.out.println("I::foo");
    }
  }

  static class A implements I {}

  static class TestClass {

    public static void main(String[] args) {
      Runnable runnable =
          () -> {
            System.out.println("Hello, world!");
          };
      runnable.run();

      new A().foo();
    }
  }
}
