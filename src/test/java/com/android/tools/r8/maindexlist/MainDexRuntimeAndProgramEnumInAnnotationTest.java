// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.maindexlist;

import static com.android.tools.r8.maindexlist.MainDexRuntimeAndProgramEnumInAnnotationTest.EnumForAnnotation.TEST;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.BooleanUtils;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MainDexRuntimeAndProgramEnumInAnnotationTest extends TestBase {

  Set<Class<?>> CUSTOM_CLASSES =
      ImmutableSet.of(
          B.class,
          Main.class,
          EnumForAnnotation.class,
          RuntimeRetentionAnnotationWithProgramEnum.class);
  Set<Class<?>> DEFAULT_CLASSES =
      Sets.union(
          CUSTOM_CLASSES,
          ImmutableSet.of(
              C.class,
              RuntimeRetentionAnnotationWithRuntimeEnum.class,
              RuntimeRetentionAnnotationWithoutEnum.class));

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean ignoreBootclasspathEnumsForMaindexTracing;

  @Parameters(name = "{0}, ignoreBootclasspathEnumsForMaindexTracing: {1}")
  public static List<Object[]> parameters() {
    return buildParameters(
        getTestParameters()
            .withDexRuntimes()
            .withApiLevelsEndingAtExcluding(apiLevelWithNativeMultiDexSupport())
            .build(),
        BooleanUtils.values());
  }

  @Test
  public void testMainDex() throws Exception {
    testForMainDexListGenerator(temp)
        .addInnerClasses(getClass())
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .addMainDexRules(
            "-keep class " + Main.class.getTypeName() + " {",
            "  public static void main(java.lang.String[]);",
            "}")
        .applyIf(
            ignoreBootclasspathEnumsForMaindexTracing,
            builder ->
                builder.addOptionsModification(
                    options -> {
                      options.ignoreBootClasspathEnumsForMaindexTracing = true;
                    }))
        .run()
        .inspectMainDexClasses(
            mainDexList -> {
              assertEquals(
                  (ignoreBootclasspathEnumsForMaindexTracing ? CUSTOM_CLASSES : DEFAULT_CLASSES)
                      .stream().map(Reference::classFromClass).collect(Collectors.toSet()),
                  new HashSet<>(mainDexList));
            });
  }

  @Test
  public void testD8() throws Exception {
    testForD8(temp)
        .addInnerClasses(getClass())
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .setMinApi(parameters)
        .applyIf(
            ignoreBootclasspathEnumsForMaindexTracing,
            builder ->
                builder.addOptionsModification(
                    options -> options.ignoreBootClasspathEnumsForMaindexTracing = true))
        .collectMainDexClasses()
        .addMainDexRules(
            "-keep class " + Main.class.getTypeName() + " {",
            "  public static void main(java.lang.String[]);",
            "}")
        .compile()
        .inspectMainDexClasses(
            mainDexClasses -> {
              assertEquals(
                  (ignoreBootclasspathEnumsForMaindexTracing ? CUSTOM_CLASSES : DEFAULT_CLASSES)
                      .stream().map(TestBase::typeName).collect(Collectors.toSet()),
                  mainDexClasses);
            });
  }

  public enum EnumForAnnotation {
    TEST
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  public @interface RuntimeRetentionAnnotationWithoutEnum {}

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  public @interface RuntimeRetentionAnnotationWithProgramEnum {

    EnumForAnnotation value() default TEST;
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  public @interface RuntimeRetentionAnnotationWithRuntimeEnum {

    ElementType value();
  }

  @RuntimeRetentionAnnotationWithoutEnum
  public static class A {

    public static void main(String[] args) {}
  }

  @RuntimeRetentionAnnotationWithProgramEnum
  public static class B {

    public static void main(String[] args) {}
  }

  @RuntimeRetentionAnnotationWithRuntimeEnum(ElementType.TYPE)
  public static class C {

    public static void main(String[] args) {}
  }

  public static class Main {

    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }
  }
}
