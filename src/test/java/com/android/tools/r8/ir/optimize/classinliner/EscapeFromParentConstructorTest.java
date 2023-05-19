// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EscapeFromParentConstructorTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public EscapeFromParentConstructorTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(EscapeFromParentConstructorTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    // The receiver escapes from BuilderBase.<init>() (except when inlined) and therefore Builder is
    // not considered eligible for class inlining.
    assertThat(
        inspector.clazz(BuilderBase.class),
        notIf(isPresent(), parameters.canInitNewInstanceUsingSuperclassConstructor()));
    assertThat(
        inspector.clazz(Builder.class),
        notIf(isPresent(), parameters.canInitNewInstanceUsingSuperclassConstructor()));
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(new Builder().build());
    }
  }

  @NoVerticalClassMerging
  static class BuilderBase {

    String greeting;

    BuilderBase() {
      initialize();
    }

    @NeverInline
    void initialize() {
      this.greeting = "Hello world!";
    }
  }

  static class Builder extends BuilderBase {

    String build() {
      return greeting;
    }
  }
}
