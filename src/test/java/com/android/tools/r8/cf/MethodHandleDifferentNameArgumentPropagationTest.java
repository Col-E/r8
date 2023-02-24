// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/* This is a regression for b/231662249 */
@RunWith(Parameterized.class)
public class MethodHandleDifferentNameArgumentPropagationTest extends TestBase {

  private static final String[] EXPECTED =
      new String[] {"ImplementationInstanceMethod::forEachWithOtherName"};

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(MethodHandleDifferentNameArgumentPropagationTest.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(MethodHandleDifferentNameArgumentPropagationTest.class)
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> options.apiModelingOptions().disableApiCallerIdentification())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  public interface Foreachable {
    void forEach(Consumer<String> consumer);
  }

  public static class ImplementationInstanceMethod {

    public void forEachWithOtherName(Consumer<String> consumer) {
      consumer.accept("ImplementationInstanceMethod::forEachWithOtherName");
    }
  }

  public static class ImplementationInstanceMethodSub extends ImplementationInstanceMethod {}

  public static class Main {

    public static void test(Foreachable foreachable) {
      foreachable.forEach(System.out::println);
    }

    public static void main(String[] args) {
      ImplementationInstanceMethodSub impl = new ImplementationInstanceMethodSub();
      test(impl::forEachWithOtherName);
    }
  }
}
