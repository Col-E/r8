// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.maindexlist;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.Reference;
import com.google.common.collect.ImmutableSet;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MainDexSourceAndClassRetentionTest extends TestBase {

  private static final Set<Class<?>> MAINDEX_CLASSES = ImmutableSet.of(Main.class);

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters()
        .withDexRuntimes()
        .withApiLevelsEndingAtExcluding(apiLevelWithNativeMultiDexSupport())
        .build();
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
        .run()
        .inspectMainDexClasses(
            mainDexList -> {
              assertEquals(
                  MAINDEX_CLASSES.stream()
                      .map(Reference::classFromClass)
                      .collect(Collectors.toSet()),
                  new HashSet<>(mainDexList));
            });
  }

  @Test
  public void testD8() throws Exception {
    testForD8(temp)
        .addInnerClasses(getClass())
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .setMinApi(parameters.getApiLevel())
        .collectMainDexClasses()
        .addMainDexRules(
            "-keep class " + Main.class.getTypeName() + " {",
            "  public static void main(java.lang.String[]);",
            "}")
        .allowStdoutMessages()
        .compile()
        .inspect(
            inspector -> {
              // Source and class retention annotation classes are still in the output, but does not
              // annotate anything.
              assertThat(inspector.clazz(SourceRetentionAnnotation.class), isPresent());
              assertThat(inspector.clazz(ClassRetentionAnnotation.class), isPresent());
              assertEquals(0, inspector.clazz(Main.class).annotations().size());
              assertEquals(0, inspector.clazz(A.class).annotations().size());
            })
        .inspectMainDexClasses(
            mainDexClasses -> {
              assertEquals(
                  MAINDEX_CLASSES.stream().map(TestBase::typeName).collect(Collectors.toSet()),
                  new HashSet<>(mainDexClasses));
            });
  }

  public enum Foo {
    TEST
  }

  public enum Bar {
    TEST
  }

  @Retention(RetentionPolicy.SOURCE)
  @Target(ElementType.TYPE)
  public @interface SourceRetentionAnnotation {

    Foo value();
  }

  @Retention(RetentionPolicy.CLASS)
  @Target(ElementType.TYPE)
  public @interface ClassRetentionAnnotation {

    Bar value();
  }

  @SourceRetentionAnnotation(Foo.TEST)
  @ClassRetentionAnnotation(Bar.TEST)
  public static class A {

    public static void main(String[] args) {}
  }

  @SourceRetentionAnnotation(Foo.TEST)
  @ClassRetentionAnnotation(Bar.TEST)
  public static class Main {

    public static void main(String[] args) {}
  }
}
