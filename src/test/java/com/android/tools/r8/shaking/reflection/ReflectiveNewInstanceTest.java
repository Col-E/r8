// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.reflection;

import static com.android.tools.r8.references.Reference.methodFromMethod;

import com.android.tools.r8.KeepUnusedArguments;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.graphinspector.GraphInspector;
import com.android.tools.r8.utils.graphinspector.GraphInspector.QueryNode;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.Constructor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ReflectiveNewInstanceTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ReflectiveNewInstanceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    String expectedOutput =
        StringUtils.lines("Success", "Success", "Success", "Success", "Success");

    if (parameters.isCfRuntime()) {
      testForJvm(parameters)
          .addTestClasspath()
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(expectedOutput);
    }

    String expectedOutputAfterR8 =
        StringUtils.lines("Success", "Success", "Success", "Fail", "Fail");

    GraphInspector inspector =
        testForR8(parameters.getBackend())
            .addInnerClasses(ReflectiveNewInstanceTest.class)
            .addKeepMainRule(TestClass.class)
            .enableGraphInspector()
            .enableUnusedArgumentAnnotations()
            .setMinApi(parameters)
            .run(parameters.getRuntime(), TestClass.class)
            .assertSuccessWithOutput(expectedOutputAfterR8)
            .graphInspector();

    QueryNode keepMain = inspector.rule(Origin.unknown(), 1, 1).assertRoot();

    MethodReference mainMethod =
        methodFromMethod(TestClass.class.getDeclaredMethod("main", String[].class));
    inspector.method(mainMethod).assertNotRenamed().assertKeptBy(keepMain);

    // Check that constructors in A, B, and C are used reflectively.
    for (Constructor<?> constructor :
        ImmutableList.of(
            A.class.getDeclaredConstructor(),
            B.class.getDeclaredConstructor(),
            C.class.getDeclaredConstructor(Object.class))) {
      inspector
          .method(methodFromMethod(constructor))
          .assertNotRenamed()
          .assertReflectedFrom(mainMethod);
    }

    // Check that constructors in D are absent.
    for (Constructor<?> constructor : D.class.getDeclaredConstructors()) {
      inspector.method(methodFromMethod(constructor)).assertAbsent();
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      Class<?> object, string;
      if (System.currentTimeMillis() >= 0) {
        object = Object.class;
        string = String.class;
      } else {
        object = String.class;
        string = Object.class;
      }

      try {
        A.class.newInstance();
        System.out.println("Success");
      } catch (Exception e) {
        System.out.println("Fail");
      }

      try {
        B.class.getDeclaredConstructor().newInstance();
        System.out.println("Success");
      } catch (Exception e) {
        System.out.println("Fail");
      }

      try {
        C.class.getDeclaredConstructor(Object.class).newInstance(new Object[] {null});
        System.out.println("Success");
      } catch (Exception e) {
        System.out.println("Fail");
      }

      try {
        D.class.getDeclaredConstructor(Object.class, object).newInstance(null, null);
        System.out.println("Success");
      } catch (Exception e) {
        System.out.println("Fail");
      }

      try {
        D.class.getDeclaredConstructor(Object.class, string).newInstance(null, null);
        System.out.println("Success");
      } catch (Exception e) {
        System.out.println("Fail");
      }
    }
  }

  static class A {}

  static class B {}

  static class C {

    @KeepUnusedArguments
    public C(Object arg) {}
  }

  static class D {

    @KeepUnusedArguments
    public D(Object arg1, Object arg2) {}

    @KeepUnusedArguments
    public D(Object arg1, String arg2) {}
  }
}
