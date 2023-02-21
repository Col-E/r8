// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.shaking.ProguardConfigurationParser.FLATTEN_PACKAGE_HIERARCHY;
import static com.android.tools.r8.shaking.ProguardConfigurationParser.REPACKAGE_CLASSES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
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
public class RepackageAnnotationTest extends RepackageTestBase {

  private static final String EXPECTED = "Hello World";
  private static final String EXPECTED_WITH_ANNOTATION_REMOVAL = "null";

  private final boolean enableProguardCompatibilityMode;
  private final boolean keepAllowShrinking;

  @Parameters(name = "{3}, compat: {0}, keep: {1}, kind: {2}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        BooleanUtils.values(),
        ImmutableList.of(FLATTEN_PACKAGE_HIERARCHY, REPACKAGE_CLASSES),
        getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public RepackageAnnotationTest(
      boolean enableProguardCompatibilityMode,
      boolean keepAllowShrinking,
      String flattenPackageHierarchyOrRepackageClasses,
      TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
    this.enableProguardCompatibilityMode = enableProguardCompatibilityMode;
    this.keepAllowShrinking = keepAllowShrinking;
  }

  @Test
  public void testRuntime() throws Exception {
    assumeFalse(enableProguardCompatibilityMode);
    assumeFalse(keepAllowShrinking);
    assumeFalse(isFlattenPackageHierarchy());
    testForRuntime(parameters)
        .addProgramClasses(Main.class, Annotation.class, A.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    assumeTrue(!enableProguardCompatibilityMode || !keepAllowShrinking);
    testForR8Compat(parameters.getBackend(), enableProguardCompatibilityMode)
        .addProgramClasses(Main.class, A.class, Annotation.class)
        .applyIf(
            keepAllowShrinking,
            builder -> {
              // Add a keep rule to ensure annotation is retained with R8 non-compat.
              assertFalse(enableProguardCompatibilityMode);
              builder.addKeepRules(
                  "-keep,allowshrinking,allowobfuscation class " + A.class.getTypeName());
            })
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .addKeepRuntimeVisibleAnnotations()
        .addKeepRules(
            "-keep,allowobfuscation @interface " + Annotation.class.getTypeName() + " {",
            "  *;",
            "}")
        .apply(this::configureRepackaging)
        .compile()
        .inspect(inspector -> assertThat(Annotation.class, isRepackaged(inspector)))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            enableProguardCompatibilityMode || keepAllowShrinking
                ? EXPECTED
                : EXPECTED_WITH_ANNOTATION_REMOVAL);
  }

  public static class Main {

    public static void main(String[] args) {
      System.out.println(new A().getAnnotationValues());
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.TYPE})
  public @interface Annotation {

    String f1();

    String f2();
  }

  @Annotation(f1 = "Hello", f2 = "World")
  @NeverClassInline
  public static class A {

    @NeverInline
    public String getAnnotationValues() {
      Annotation annotation = A.class.getAnnotation(Annotation.class);
      if (annotation == null) {
        return null;
      }
      return annotation.f1() + " " + annotation.f2();
    }
  }
}
