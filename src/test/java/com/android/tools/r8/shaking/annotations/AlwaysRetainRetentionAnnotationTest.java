// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.annotations;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.onlyIf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.shaking.enums.EnumInAnnotationTest.MyAnnotation;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.AnnotationSubject;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
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
public class AlwaysRetainRetentionAnnotationTest extends TestBase {

  private final boolean enableProguardCompatibilityMode;
  private final boolean keepAllowShrinking;
  private final TestParameters parameters;

  @Parameters(name = "{2}, compat: {0}, keep: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        BooleanUtils.values(),
        getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public AlwaysRetainRetentionAnnotationTest(
      boolean enableProguardCompatibilityMode,
      boolean keepAllowShrinking,
      TestParameters parameters) {
    this.enableProguardCompatibilityMode = enableProguardCompatibilityMode;
    this.keepAllowShrinking = keepAllowShrinking;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    assumeTrue(!enableProguardCompatibilityMode || !keepAllowShrinking);
    testForR8Compat(parameters.getBackend(), enableProguardCompatibilityMode)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addKeepRuntimeVisibleAnnotations()
        .applyIf(
            keepAllowShrinking,
            builder -> {
              assertFalse(enableProguardCompatibilityMode);
              builder.addKeepRules(
                  "-keep,allowshrinking,allowobfuscation class "
                      + MyAnnotation.class.getTypeName());
            })
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(
            inspector -> {
              ClassSubject annotationClassSubject = inspector.clazz(MyAnnotation.class);
              assertThat(annotationClassSubject, isPresent());

              AnnotationSubject retentionAnnotationSubject =
                  annotationClassSubject.annotation(Retention.class.getTypeName());
              assertThat(retentionAnnotationSubject, isPresent());

              AnnotationSubject targetAnnotationSubject =
                  annotationClassSubject.annotation(Target.class.getTypeName());
              assertThat(targetAnnotationSubject, onlyIf(shouldOnlyRetainRetention(), isAbsent()));

              AnnotationSubject myAnnotationAnnotationSubject =
                  annotationClassSubject.annotation(MyAnnotation.class.getTypeName());
              assertThat(
                  myAnnotationAnnotationSubject, onlyIf(shouldOnlyRetainRetention(), isAbsent()));
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(shouldOnlyRetainRetention() ? "1" : "3");
  }

  private boolean shouldOnlyRetainRetention() {
    return !enableProguardCompatibilityMode && !keepAllowShrinking;
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(MyAnnotation.class.getAnnotations().length);
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.TYPE})
  @MyAnnotation
  @interface MyAnnotation {}
}
