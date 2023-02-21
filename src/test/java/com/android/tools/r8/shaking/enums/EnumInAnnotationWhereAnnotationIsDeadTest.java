// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.enums;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnumInAnnotationWhereAnnotationIsDeadTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_RESULT = StringUtils.lines("TEST_ONE");

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepEnumsRule()
        .setMinApi(parameters)
        .addKeepRuntimeVisibleAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .inspect(
            inspector -> {
              assertThat(
                  inspector.clazz(Enum.class).uniqueFieldWithOriginalName("TEST_ONE"),
                  isPresentAndRenamed());
              assertThat(
                  inspector.clazz(Enum.class).uniqueFieldWithOriginalName("TEST_TWO"), isAbsent());
            })
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  public enum Enum {
    TEST_ONE,
    TEST_TWO
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.TYPE})
  public @interface MyAnnotation {

    Enum value();
  }

  @MyAnnotation(value = Enum.TEST_ONE)
  public static class ClassWithEnumAnnotation {}

  public static class Main {

    private static boolean alwaysFalse() {
      return false;
    }

    public static void main(String[] args) {
      if (alwaysFalse()) {
        System.out.println(ClassWithEnumAnnotation.class.getAnnotation(MyAnnotation.class).value());
      }
      System.out.println(Enum.TEST_ONE);
    }
  }
}
