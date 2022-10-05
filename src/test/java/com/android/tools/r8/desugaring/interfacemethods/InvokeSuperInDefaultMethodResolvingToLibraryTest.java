// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugaring.interfacemethods;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import java.util.function.Function;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeSuperInDefaultMethodResolvingToLibraryTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  private static final String EXPECTED_OUTPUT = "8";

  private void inspect(CodeInspector inspector) {
    assertTrue(
        inspector
            .clazz(B.class)
            .uniqueMethodWithOriginalName("compose")
            .streamInstructions()
            .filter(InstructionSubject::isInvoke)
            .map(invoke -> invoke.getMethod().getHolderType().toString())
            .noneMatch(name -> name.endsWith("$-CC")));
  }

  private String getExpectedOutputForApiCheck() {
    return parameters.getApiLevel().isLessThan(AndroidApiLevel.N) ? "No call" : EXPECTED_OUTPUT;
  }

  @Test
  public void testDesugaring() throws Exception {
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.T))
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            parameters.isDexRuntime()
                && parameters
                    .getRuntime()
                    .asDex()
                    .maxSupportedApiLevel()
                    .isLessThan(AndroidApiLevel.N),
            r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class),
            r -> r.assertSuccessWithOutputLines(EXPECTED_OUTPUT));
  }

  @Test
  public void testDesugaringWithApiLevelCheck() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.T))
        .addAndroidBuildVersion(parameters.getApiLevel())
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClassWithApiLevelCheck.class)
        .assertSuccessWithOutputLines(getExpectedOutputForApiCheck());
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.T))
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            parameters.isDexRuntime()
                && parameters
                    .getRuntime()
                    .asDex()
                    .maxSupportedApiLevel()
                    .isLessThan(AndroidApiLevel.N),
            r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class),
            r -> r.assertSuccessWithOutputLines(EXPECTED_OUTPUT));
  }

  @Test
  public void testR8WithApiLevelCheck() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.T))
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(TestClassWithApiLevelCheck.class)
        .addAndroidBuildVersion(parameters.getApiLevel())
        .run(parameters.getRuntime(), TestClassWithApiLevelCheck.class)
        .assertSuccessWithOutputLines(getExpectedOutputForApiCheck());
  }

  static class TestClass {

    private static void m(C c) {
      System.out.println(c.compose(c).apply(2));
    }

    public static void main(String[] args) {
      m(new C());
    }
  }

  static class TestClassWithApiLevelCheck {

    private static void m(C c) {
      System.out.println(c.compose(c).apply(2));
    }

    public static void main(String[] args) {
      if (AndroidBuildVersion.VERSION >= 24) {
        m(new C());
      } else {
        System.out.println("No call");
      }
    }
  }

  interface MyFunction<V, R> extends Function<V, R> {}

  abstract static class B<V, R> implements MyFunction<V, R> {

    @Override
    public <V1> Function<V1, R> compose(Function<? super V1, ? extends V> before) {
      return MyFunction.super.compose(before);
    }
  }

  static class C extends B<Integer, Integer> {

    @Override
    public Integer apply(Integer integer) {
      return integer * 2;
    }
  }
}
