// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.unusedarguments;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UnusedArgumentsInstanceConstructorTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public UnusedArgumentsInstanceConstructorTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("Hello world");

    if (parameters.isCfRuntime()) {
      testForJvm(parameters)
          .addTestClasspath()
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(expectedOutput);
    }

    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addInnerClasses(UnusedArgumentsInstanceConstructorTest.class)
            .addKeepMainRule(TestClass.class)
            .addHorizontallyMergedClassesInspector(
                HorizontallyMergedClassesInspector::assertNoClassesMerged)
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .enableNoHorizontalClassMergingAnnotations()
            .setMinApi(parameters)
            .run(parameters.getRuntime(), TestClass.class)
            .assertSuccessWithOutput(expectedOutput)
            .inspector();

    ClassSubject classSubject = inspector.clazz(A.class);
    assertThat(classSubject, isPresent());

    MethodSubject methodSubject = classSubject.uniqueMethodWithOriginalName("<init>");
    assertThat(methodSubject, isPresent());
    assertTrue(methodSubject.getMethod().getReference().proto.parameters.isEmpty());

    assertThat(inspector.clazz(B.class), not(isPresent()));
    assertThat(inspector.clazz(C.class), not(isPresent()));
  }

  static class TestClass {

    public static void main(String[] args) {
      new A(null, new C()).doSomething();
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class A {

    @NeverInline
    public A(B uninstantiated, C unused) {
      System.out.print("Hello");
      if (uninstantiated != null) {
        throw new RuntimeException("Unreachable");
      } else {
        System.out.print(" ");
      }
    }

    @NeverInline
    public void doSomething() {
      System.out.println("world");
    }
  }

  @NoHorizontalClassMerging
  static class B {}

  @NoHorizontalClassMerging
  static class C {}
}
