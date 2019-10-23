// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.annotations;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ProgramAnnotationRemovalTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ProgramAnnotationRemovalTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addInnerClasses(ProgramAnnotationRemovalTest.class)
            .addKeepMainRule(TestClass.class)
            .addKeepAttributes("RuntimeVisibleAnnotations")
            .setMinApi(parameters.getApiLevel())
            .compile()
            .run(parameters.getRuntime(), TestClass.class);

    CodeInspector inspector = result.inspector();

    ClassSubject liveAnnotationClassSubject = inspector.clazz(LiveProgramAnnotation.class);
    assertThat(liveAnnotationClassSubject, isPresent());

    ClassSubject deadAnnotationClassSubject = inspector.clazz(DeadProgramAnnotation.class);
    assertThat(deadAnnotationClassSubject, not(isPresent()));

    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertThat(testClassSubject, isPresent());

    MethodSubject methodWithLiveProgramAnnotationSubject =
        testClassSubject.uniqueMethodWithName("methodWithLiveProgramAnnotation");
    assertThat(methodWithLiveProgramAnnotationSubject, isPresent());
    assertEquals(1, methodWithLiveProgramAnnotationSubject.getMethod().annotations.size());

    MethodSubject methodWithDeadProgramAnnotationSubject =
        testClassSubject.uniqueMethodWithName("methodWithDeadProgramAnnotation");
    assertThat(methodWithDeadProgramAnnotationSubject, isPresent());
    assertEquals(0, methodWithDeadProgramAnnotationSubject.getMethod().annotations.size());

    result.assertSuccessWithOutputLines("@" + liveAnnotationClassSubject.getFinalName() + "()");
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      methodWithLiveProgramAnnotation();
      methodWithDeadProgramAnnotation();

      // Do something with LiveProgramAnnotation to ensure it becomes live.
      if (System.currentTimeMillis() <= 0) {
        System.out.println(LiveProgramAnnotation.class);
      }
    }

    @NeverInline
    @LiveProgramAnnotation
    static void methodWithLiveProgramAnnotation() throws Exception {
      Method method = TestClass.class.getDeclaredMethod("methodWithLiveProgramAnnotation");
      for (Annotation annotation : method.getDeclaredAnnotations()) {
        System.out.println(annotation);
      }
    }

    @NeverInline
    @DeadProgramAnnotation
    static void methodWithDeadProgramAnnotation() throws Exception {
      Method method = TestClass.class.getDeclaredMethod("methodWithDeadProgramAnnotation");
      for (Annotation annotation : method.getDeclaredAnnotations()) {
        System.out.println(annotation);
      }
    }
  }

  @Target(ElementType.METHOD)
  @Retention(RetentionPolicy.RUNTIME)
  @interface LiveProgramAnnotation {}

  @Target(ElementType.METHOD)
  @Retention(RetentionPolicy.RUNTIME)
  @interface DeadProgramAnnotation {}
}
