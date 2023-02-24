// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.negatedrules;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NegatedMethodArgumentTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("Hello, world!");

  @Test
  public void testR8NegatedPrimitive() {
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForR8(parameters.getBackend())
                .addInnerClasses(getClass())
                .addKeepMainRule(TestClass.class)
                .setMinApi(parameters)
                .addKeepRules("-keep class * { void setX(!%); }")
                .run(parameters.getRuntime(), TestClass.class)
                .assertSuccessWithOutput(EXPECTED_OUTPUT));
  }

  @Test
  public void testR8NegatedClass() {
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForR8(parameters.getBackend())
                .addInnerClasses(getClass())
                .addKeepMainRule(TestClass.class)
                .setMinApi(parameters)
                .addKeepRules("-keep class * { void setX(!**Producer); }")
                .compile());
  }

  private void classPresentWithOnlyInstanceInitializer(ClassSubject subject) {
    assertThat(subject, isPresent());
    assertTrue(subject.allMethods().stream().allMatch(FoundMethodSubject::isInstanceInitializer));
  }

  @Test
  public void testProguardNegatedPrimitive() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForProguard(ProguardVersion.V7_0_0)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-keep class * { void setX(!%); }")
        .addKeepRules("-dontwarn **.NegatedMethodArgumentTest")
        .addDontObfuscate()
        .run(parameters.getRuntime(), TestClass.class)
        .inspect(
            inspector -> {
              classPresentWithOnlyInstanceInitializer(inspector.clazz(A.class));
              classPresentWithOnlyInstanceInitializer(inspector.clazz(B.class));
            })
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testProguardNegatedClass() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForProguard(ProguardVersion.V7_0_0)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-keep class * { void setX(!**.*P1); }")
        .addKeepRules("-dontwarn **.NegatedMethodArgumentTest")
        .addDontObfuscate()
        .run(parameters.getRuntime(), TestClass.class)
        .inspect(
            inspector -> {
              classPresentWithOnlyInstanceInitializer(inspector.clazz(A.class));
              classPresentWithOnlyInstanceInitializer(inspector.clazz(B.class));
            })
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  static class P1 {}

  static class P2 {}

  static class A {

    public void setX(int x) {}

    public void setX(P1 x) {}
  }

  static class B {

    public void setX(int x) {}

    public void setX(P2 x) {}
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }
  }
}
