// Copyright (c) 2018 the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.KeepConstantArguments;
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
  @KeepConstantArguments
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
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
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
        .enableConstantArgumentAnnotations()
        .setMinApi(parameters)
        .addOptionsModification(o -> o.inlinerOptions().enableInlining = false)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT)
        .inspect(
            inspector -> {
              ClassSubject returnType = inspector.clazz(B112517039ReturnType.class);
              assertThat(returnType, isPresentAndRenamed());
            });
  }
}
