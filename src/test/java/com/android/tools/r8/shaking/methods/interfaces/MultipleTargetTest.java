// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.methods.interfaces;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MultipleTargetTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public MultipleTargetTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws ExecutionException, CompilationFailedException, IOException {
    assumeTrue(
        parameters.isCfRuntime()
            || parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N));
    testForRuntime(parameters)
        .addProgramClasses(Top.class, Left.class, Right.class, Bottom.class, A.class, Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(A.class.getName());
  }

  @Test
  public void testSingleInheritanceR8BeforeNougat()
      throws CompilationFailedException, IOException, ExecutionException {
    assumeTrue(parameters.isDexRuntime() && parameters.getApiLevel().isLessThan(AndroidApiLevel.N));
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(Top.class, Left.class, Right.class, Bottom.class)
            .setMinApi(parameters)
            .addKeepMethodRules(Bottom.class, "java.lang.String name()")
            .addDontObfuscate()
            .compile();
    // TODO(b/144269679): We should be able to compile and run this.
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, Main.class)
        .addClasspathClasses(Top.class, Left.class, Right.class, Bottom.class)
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .addRunClasspathFiles(compileResult.writeToZip())
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatMatches(
            containsString(
                "com.android.tools.r8.shaking.methods.interfaces.MultipleTargetTest$Left$-CC"));
  }

  @Test
  public void testKeepingBottomName()
      throws CompilationFailedException, IOException, ExecutionException {
    assumeTrue(
        parameters.isCfRuntime()
            || parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N));
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(Top.class, Left.class, Right.class, Bottom.class)
            .addKeepMethodRules(Bottom.class, "java.lang.String name()")
            .setMinApi(parameters)
            .compile();
    testForRuntime(parameters)
        .addProgramClasses(A.class, Main.class)
        .addRunClasspathFiles(compileResult.writeToZip())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(A.class.getName());
  }

  interface Top {
    default String name() {
      return "unnamed";
    }
  }

  interface Left extends Top {
    default String name() {
      return getClass().getName();
    }
  }

  interface Right extends Top {
    /* No override of default String name() */
  }

  interface Bottom extends Left, Right {}

  public static class A implements Bottom {}

  public static class Main {

    public static void main(String[] args) {
      System.out.println(new A().name());
    }
  }
}
