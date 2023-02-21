// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.annotations.annotationdefault;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DefaultValueForAnnotationWithExplicitValueShrinkingTest extends TestBase {

  @Parameter(0)
  public boolean enableProguardCompatibilityMode;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, compat: {0}")
  public static List<Object[]> params() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withDexRuntimes().withAllApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    testForR8Compat(parameters.getBackend(), enableProguardCompatibilityMode)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepAttributeAnnotationDefault()
        .addKeepRuntimeVisibleAnnotations()
        .setMinApi(parameters)
        .compile()
        .apply(
            compileResult -> {
              CodeInspector inspector = compileResult.inspector();

              // MyAnnotation has a @Retention annotation and an @AnnotationDefault annotation.
              // TODO(b/191741002): The default value for MyAnnotation.value() is unused, thus there
              // is no need to retain the @AnnotationDefault annotation.
              ClassSubject annotationClassSubject = inspector.clazz(MyAnnotation.class);
              assertThat(annotationClassSubject, isPresent());
              assertThat(
                  annotationClassSubject.annotation(Retention.class.getTypeName()), isPresent());
              assertThat(
                  annotationClassSubject.annotation("dalvik.annotation.AnnotationDefault"),
                  isPresent());
              assertEquals(2, annotationClassSubject.getDexProgramClass().annotations().size());

              compileResult
                  .run(parameters.getRuntime(), Main.class)
                  .assertSuccessWithOutputLines(Main.class.getTypeName());
            });
  }

  @MyAnnotation(value = Main.class)
  static class Main {
    public static void main(String[] args) {
      MyAnnotation myAnnotation = Main.class.getAnnotation(MyAnnotation.class);
      System.out.println(myAnnotation.value().getName());
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  @interface MyAnnotation {
    Class<?> value() default Object.class;
  }
}
