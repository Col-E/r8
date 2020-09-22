// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DesugarToClassFile extends TestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  private final TestParameters parameters;

  public DesugarToClassFile(TestParameters parameters) {
    this.parameters = parameters;
  }

  private void checkHasCompanionClassIfRequired(CodeInspector inspector) {
    boolean canUseDefaultAndStaticInterfaceMethods =
        parameters
            .getApiLevel()
            .isGreaterThanOrEqualTo(apiLevelWithDefaultInterfaceMethodsSupport());
    if (canUseDefaultAndStaticInterfaceMethods) {
      assertTrue(
          inspector.allClasses().stream()
              .noneMatch(subject -> subject.getOriginalName().endsWith("$-CC")));
    } else {
      assertTrue(
          inspector.allClasses().stream()
              .anyMatch(subject -> subject.getOriginalName().endsWith("$-CC")));
    }
  }

  private void checkHasLambdaClass(CodeInspector inspector) {
    assertTrue(
        inspector.allClasses().stream()
            .anyMatch(subject -> subject.getOriginalName().contains("-$$Lambda$")));
  }

  @Test
  public void test() throws Exception {
    // Use D8 to desugar with Java classfile output.
    Path jar =
        testForD8(Backend.CF)
            .addInnerClasses(DesugarToClassFile.class)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .inspect(this::checkHasCompanionClassIfRequired)
            .inspect(this::checkHasLambdaClass)
            .writeToZip();

    if (parameters.getRuntime().isCf()) {
      // Run on the JVM.
      testForJvm()
          .addProgramFiles(jar)
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutputLines("Hello, world!", "I::foo", "J::bar", "42");
    } else {
      assert parameters.getRuntime().isDex();
      // Convert to DEX without desugaring.
      testForD8()
          .addProgramFiles(jar)
          .setMinApi(parameters.getApiLevel())
          .disableDesugaring()
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutputLines("Hello, world!", "I::foo", "J::bar", "42");
    }
  }

  interface I {
    default void foo() {
      System.out.println("I::foo");
    }
  }

  interface J {
    static void bar() {
      System.out.println("J::bar");
    }
  }

  static class A implements I {}

  static class TestClass {

    public static void main(String[] args) {
      lambda();
      defaultInterfaceMethod();
      staticInterfaceMethod();
      backport();
    }

    public static void lambda() {
      Runnable runnable =
          () -> {
            System.out.println("Hello, world!");
          };
      runnable.run();
    }

    public static void defaultInterfaceMethod() {
      new A().foo();
    }

    public static void staticInterfaceMethod() {
      J.bar();
    }

    public static void backport() {
      System.out.println(Long.hashCode(42));
    }
  }
}
