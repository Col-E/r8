// Copyright (c) 2018 the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

class B112517039ReturnType extends Exception {
}

interface B112517039I {
  B112517039ReturnType m();
  void flaf(Exception e);
}

class B112517039Caller {
  public void call(B112517039I i) {
    System.out.println("Ewwo!");
    i.flaf(i.m());
  }
}

class B112517039Main {
  public static void main(String[] args) {
    try {
      B112517039Caller caller = new B112517039Caller();
      caller.call(null);
    } catch (NullPointerException e) {
      System.out.println("NullPointerException");
    }
  }
}

@RunWith(Parameterized.class)
public class ReturnTypeTest extends TestBase {
  private static final String JAVA_OUTPUT = StringUtils.lines(
      "Ewwo!",
      "NullPointerException"
  );
  private static final Class<?> MAIN = B112517039Main.class;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private final TestParameters parameters;

  public ReturnTypeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJVMOutput() throws Exception {
    assumeTrue("Only run JVM reference on CF runtimes", parameters.isCfRuntime());
    testForJvm()
        .addTestClasspath()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(
            B112517039ReturnType.class, B112517039I.class, B112517039Caller.class, MAIN)
        .addKeepMainRule(MAIN)
        .setMinApi(parameters.getApiLevel())
        .addOptionsModification(
            o -> {
              // No actual implementation of B112517039I, rather invoked with `null`.
              // Call site optimization propagation will conclude that the input of B...Caller#call
              // is
              // always null, and replace the last call with null-throwing instruction.
              // However, we want to test return type and parameter type are kept in this scenario.
              o.callSiteOptimizationOptions().disableTypePropagationForTesting();
              o.enableInlining = false;
            })
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT)
        .inspect(
            inspector -> {
              ClassSubject returnType = inspector.clazz(B112517039ReturnType.class);
              assertThat(returnType, isPresentAndRenamed());
            });
  }
}
