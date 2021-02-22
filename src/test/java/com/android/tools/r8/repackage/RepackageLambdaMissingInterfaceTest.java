// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RepackageLambdaMissingInterfaceTest extends RepackageTestBase {

  public RepackageLambdaMissingInterfaceTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void testR8WithoutRepackaging() throws Exception {
    runTest(false)
        .assertFailureWithErrorThatThrowsIf(parameters.isDexRuntime(), AbstractMethodError.class)
        .assertSuccessWithOutputLinesIf(parameters.isCfRuntime(), "0");
  }

  @Test
  public void testR8() throws Exception {
    runTest(true)
        .assertFailureWithErrorThatThrowsIf(parameters.isDexRuntime(), AbstractMethodError.class)
        .assertSuccessWithOutputLinesIf(parameters.isCfRuntime(), "0");
  }

  private R8TestRunResult runTest(boolean repackage) throws Exception {
    return testForR8(parameters.getBackend())
        .addProgramClasses(ClassWithLambda.class, Main.class)
        .addKeepMainRule(Main.class)
        .addKeepAttributeInnerClassesAndEnclosingMethod()
        .applyIf(repackage, this::configureRepackaging)
        .setMinApi(parameters.getApiLevel())
        .addDontWarn(MissingInterface.class)
        .noClassInlining()
        .enableInliningAnnotations()
        .compile()
        .inspect(
            inspector -> {
              // Find the generated lambda class
              assertThat(
                  ClassWithLambda.class,
                  isRepackagedIf(inspector, repackage && parameters.isDexRuntime()));
              if (!parameters.isDexRuntime()) {
                return;
              }
              inspector.forAllClasses(
                  clazz -> {
                    if (clazz.isSynthesizedJavaLambdaClass()) {
                      assertThat(
                          clazz.getFinalName(), containsString(Main.class.getPackage().getName()));
                    }
                  });
            })
        .addRunClasspathClasses(MissingInterface.class)
        .run(parameters.getRuntime(), Main.class);
  }

  interface MissingInterface {

    void bar(int x);
  }

  public static class ClassWithLambda {

    @NeverInline
    public static void callWithLambda() {
      Main.foo(System.out::println);
    }
  }

  public static class Main {

    private static int argCount;

    @NeverInline
    public static void foo(MissingInterface i) {
      i.bar(argCount);
    }

    public static void main(String[] args) {
      argCount = args.length;
      ClassWithLambda.callWithLambda();
    }
  }
}
