// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.allowshrinking;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestShrinkerBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeepMethodNameCompatibilityTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public KeepMethodNameCompatibilityTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testProguard() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    test(testForProguard(ProguardVersion.getLatest()).addDontWarn(getClass()));
  }

  @Test
  public void testR8() throws Exception {
    test(testForR8(parameters.getBackend()));
  }

  private <T extends TestShrinkerBuilder<?, ?, ?, ?, T>> void test(T testBuilder) throws Exception {
    testBuilder
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addKeepRules(
            "-keepclassmembernames class " + TestClass.class.getTypeName() + " { void test(); }")
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(
            inspector ->
                assertThat(
                    inspector.clazz(TestClass.class).uniqueMethodWithName("test"), isPresent()))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("foo");
  }

  static class TestClass {

    public static void main(String[] args) {
      test();
    }

    static void test() {
      System.out.println("foo");
    }
  }
}
