// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.ifrule;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IfOnAnnotationWithWildcardTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public IfOnAnnotationWithWildcardTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8WithTripleStars() throws Exception {
    runTest(
        "-if class ** { @"
            + MyAnnotation.class.getTypeName()
            + " *** *; }\n"
            + "-keep class <1> { *; }");
  }

  @Test
  public void testR8WithFields() throws Exception {
    runTest(
        "-if class ** { @"
            + MyAnnotation.class.getTypeName()
            + " <fields>; }\n"
            + "-keep class <1> { *; }");
  }

  @Test
  public void testR8WithAny() throws Exception {
    runTest(
        "-if class ** { @"
            + MyAnnotation.class.getTypeName()
            + " *; }\n"
            + "-keep class <1> { *; }");
  }

  private void runTest(String ifRule) throws Exception {
    testForR8Compat(parameters.getBackend())
        .addProgramClasses(MyClass.class, MyAnnotation.class)
        .setMinApi(parameters)
        .addKeepClassAndMembersRules(MyAnnotation.class)
        .addKeepRules("-keep class * { @" + MyAnnotation.class.getTypeName() + " <fields>; }")
        .addKeepRules(ifRule)
        .addRunClasspathFiles(buildOnDexRuntime(parameters, Main.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World = 42");
  }

  public static class MyClass {
    @MyAnnotation int x;
    int y;

    public void foo() {
      System.out.println("Hello World = " + y);
    }
  }

  public @interface MyAnnotation {}

  public static class Main {

    public static void main(String[] args) {
      final MyClass myClass = new MyClass();
      myClass.y = 42;
      myClass.foo();
    }
  }
}
