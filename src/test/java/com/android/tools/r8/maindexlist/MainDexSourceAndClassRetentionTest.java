// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.maindexlist;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableSet;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MainDexSourceAndClassRetentionTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimes()
        .withApiLevelsEndingAtExcluding(apiLevelWithNativeMultiDexSupport())
        .build();
  }

  public MainDexSourceAndClassRetentionTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testMainDex() throws Exception {
    List<ClassReference> mainDexList =
        testForMainDexListGenerator(temp)
            .addInnerClasses(getClass())
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.B))
            .addMainDexRules(
                "-keep class " + Main.class.getTypeName() + " {",
                "  public static void main(java.lang.String[]);",
                "}")
            .run()
            .getMainDexList();
    // TODO(b/186090713): {Foo, BAR} and {Source,Class}RetentionAnnotation should not be included.
    assertEquals(
        ImmutableSet.of(
            Reference.classFromClass(Foo.class),
            Reference.classFromClass(Bar.class),
            Reference.classFromClass(Main.class),
            Reference.classFromClass(ClassRetentionAnnotation.class),
            Reference.classFromClass(SourceRetentionAnnotation.class)),
        new HashSet<>(mainDexList));
  }

  @Test
  public void testD8() throws Exception {
    Set<String> mainDexClasses =
        testForD8(temp)
            .addInnerClasses(getClass())
            .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
            .setMinApi(parameters.getApiLevel())
            .collectMainDexClasses()
            .addMainDexRules(
                "-keep class " + Main.class.getTypeName() + " {",
                "  public static void main(java.lang.String[]);",
                "}")
            .compile()
            .getMainDexClasses();
    // TODO(b/186090713): {Foo, BAR} and {Source,Class}RetentionAnnotation should not be included.
    assertEquals(
        ImmutableSet.of(
            typeName(Foo.class),
            typeName(Bar.class),
            typeName(Main.class),
            typeName(ClassRetentionAnnotation.class),
            typeName(SourceRetentionAnnotation.class)),
        mainDexClasses);
  }

  public enum Foo {
    TEST;
  }

  public enum Bar {
    TEST;
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
  public static class Main {

    public static void main(String[] args) {}
  }
}
