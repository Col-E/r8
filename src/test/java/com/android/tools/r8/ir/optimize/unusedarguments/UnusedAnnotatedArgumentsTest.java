// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.unusedarguments;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.KeepUnusedArguments;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UnusedAnnotatedArgumentsTest extends TestBase {

  private final boolean keepUnusedArguments;
  private final TestParameters parameters;

  @Parameters(name = "{1}, keep unused arguments: {0}")
  public static List<Object[]> params() {
    return buildParameters(BooleanUtils.values(), getTestParameters().withAllRuntimes().build());
  }

  public UnusedAnnotatedArgumentsTest(boolean keepUnusedArguments, TestParameters parameters) {
    this.keepUnusedArguments = keepUnusedArguments;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    try {
      testForR8(parameters.getBackend())
          .addInnerClasses(UnusedAnnotatedArgumentsTest.class)
          .addKeepMainRule(TestClass.class)
          .addKeepClassRules(Unused.class)
          .addKeepAttributes("RuntimeVisibleParameterAnnotations")
          .enableInliningAnnotations()
          .enableUnusedArgumentAnnotations(keepUnusedArguments)
          .setMinApi(parameters.getRuntime())
          .compile()
          .inspect(this::verifyOutput)
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutputLines("Hello world!");
      assertTrue(keepUnusedArguments);
    } catch (ArrayIndexOutOfBoundsException | AssertionError e) {
      // TODO(b/131663970): Fix unused argument removal.
      assertFalse(keepUnusedArguments);
    }
  }

  private void verifyOutput(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());

    MethodSubject methodSubject =
        // TODO(b/123060011): Mapping not working in presence of unused argument removal.
        classSubject.uniqueMethodWithName(keepUnusedArguments ? "test" : "a");
    assertThat(methodSubject, isPresent());

    if (keepUnusedArguments) {
      assertEquals(1, methodSubject.getMethod().method.proto.parameters.size());
      assertEquals(1, methodSubject.getMethod().parameterAnnotationsList.size());
    } else {
      assertEquals(0, methodSubject.getMethod().method.proto.parameters.size());
      assertEquals(0, methodSubject.getMethod().parameterAnnotationsList.size());
    }

    System.out.println();
  }

  static class TestClass {

    public static void main(String[] args) {
      test(null);
    }

    @KeepUnusedArguments
    @NeverInline
    static void test(@Unused Object unused) {
      System.out.println("Hello world!");
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.PARAMETER)
  @interface Unused {}
}
