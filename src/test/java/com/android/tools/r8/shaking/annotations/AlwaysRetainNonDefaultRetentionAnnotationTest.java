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
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.google.common.collect.ImmutableList;
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
public class AlwaysRetainNonDefaultRetentionAnnotationTest extends TestBase {

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

  public AlwaysRetainNonDefaultRetentionAnnotationTest(
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
        .addKeepRuntimeInvisibleAnnotations()
        .addKeepRuntimeVisibleAnnotations()
        .applyIf(
            keepAllowShrinking,
            builder -> {
              assertFalse(enableProguardCompatibilityMode);
              builder.addKeepRules(
                  "-keep,allowshrinking,allowobfuscation class "
                      + MyClassAnnotation.class.getTypeName());
              builder.addKeepRules(
                  "-keep,allowshrinking,allowobfuscation class "
                      + MyRuntimeAnnotation.class.getTypeName());
            })
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject classAnnotationClassSubject = inspector.clazz(MyClassAnnotation.class);
              assertThat(classAnnotationClassSubject, isPresent());
              assertThat(
                  classAnnotationClassSubject.annotation(Retention.class.getTypeName()),
                  onlyIf(isFullModeWithoutKeepRule(), isAbsent()));
              assertThat(
                  classAnnotationClassSubject.annotation(Target.class.getTypeName()),
                  onlyIf(isFullModeWithoutKeepRule(), isAbsent()));
              assertThat(
                  classAnnotationClassSubject.annotation(MyClassAnnotation.class.getTypeName()),
                  onlyIf(isFullModeWithoutKeepRule(), isAbsent()));

              ClassSubject runtimeAnnotationClassSubject =
                  inspector.clazz(MyRuntimeAnnotation.class);
              assertThat(runtimeAnnotationClassSubject, isPresent());
              assertThat(
                  runtimeAnnotationClassSubject.annotation(Retention.class.getTypeName()),
                  isPresent());
              assertThat(
                  runtimeAnnotationClassSubject.annotation(Target.class.getTypeName()),
                  onlyIf(isFullModeWithoutKeepRule(), isAbsent()));
              assertThat(
                  runtimeAnnotationClassSubject.annotation(MyRuntimeAnnotation.class.getTypeName()),
                  onlyIf(isFullModeWithoutKeepRule(), isAbsent()));
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(
            isFullModeWithoutKeepRule() ? ImmutableList.of("0", "1") : ImmutableList.of("2", "3"));
  }

  private boolean isFullModeWithoutKeepRule() {
    return !enableProguardCompatibilityMode && !keepAllowShrinking;
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(MyClassAnnotation.class.getAnnotations().length);
      System.out.println(MyRuntimeAnnotation.class.getAnnotations().length);
    }
  }

  @Retention(RetentionPolicy.CLASS)
  @Target({ElementType.TYPE})
  @MyClassAnnotation
  @interface MyClassAnnotation {}

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.TYPE})
  @MyRuntimeAnnotation
  @interface MyRuntimeAnnotation {}
}
