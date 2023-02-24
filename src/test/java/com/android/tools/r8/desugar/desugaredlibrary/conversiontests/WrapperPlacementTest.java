// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class WrapperPlacementTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  private static final String EXPECTED = StringUtils.lines("[1, 2, 3]", "[2, 3, 4]");

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getJdk8Jdk11(),
        ImmutableList.of(D8_L8DEBUG));
  }

  public WrapperPlacementTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void testReference() throws Exception {
    Assume.assumeTrue(
        "No need to test twice",
        parameters.isCfRuntime()
            && libraryDesugaringSpecification == JDK8
            && compilationSpecification.isProgramShrink());
    testForJvm(parameters)
        .addAndroidBuildVersion()
        .addProgramClassesAndInnerClasses(MyArrays1.class)
        .addProgramClassesAndInnerClasses(MyArrays2.class)
        .addProgramClasses(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testNoWrappers() throws Throwable {
    assumeTrue(parameters.isDexRuntime());
    // No wrappers are made during program compilation.
    Path path1 = compileWithCoreLibraryDesugaring(MyArrays1.class);
    Path path2 = compileWithCoreLibraryDesugaring(MyArrays2.class);

    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramClasses(TestClass.class)
        .addAndroidBuildVersion()
        .compile()
        .inspect(this::assertNoWrappers)
        .inspectL8(this::assertCoreLibContainsWrappers)
        .addRunClasspathFiles(path1, path2)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  private Path compileWithCoreLibraryDesugaring(Class<?> clazz) throws Throwable {
    return testForDesugaredLibrary(
            parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramClassesAndInnerClasses(clazz)
        .compile()
        .inspect(this::assertNoWrappers)
        .writeToZip();
  }

  private boolean hasNativeIntUnaryOperator() {
    return parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N);
  }

  private void assertCoreLibContainsWrappers(CodeInspector inspector) {
    if (!hasNativeIntUnaryOperator()) {
      Stream<FoundClassSubject> wrappers = getWrappers(inspector);
      assertNotEquals(0, wrappers.count());
    }
  }

  private void assertNoWrappers(CodeInspector inspector) {
    Stream<FoundClassSubject> wrappers = getWrappers(inspector);
    assertEquals(0, wrappers.count());
  }

  private Stream<FoundClassSubject> getWrappers(CodeInspector inspector) {
    return inspector.allClasses().stream()
        .filter(c -> SyntheticItemsTestUtils.isWrapper(c.getOriginalReference()));
  }

  static class MyArrays1 {

    interface IntGenerator {
      int generate(int index);
    }

    public static void setAll(int[] ints, IntGenerator generator) {
      if (AndroidBuildVersion.VERSION >= 24) {
        java.util.Arrays.setAll(ints, generator::generate);
      } else {
        for (int i = 0; i < ints.length; i++) {
          ints[i] = generator.generate(i);
        }
      }
    }
  }

  static class MyArrays2 {

    interface IntGenerator {
      int generate(int index);
    }

    public static void setAll(int[] ints, IntGenerator generator) {
      if (AndroidBuildVersion.VERSION >= 24) {
        java.util.Arrays.setAll(ints, generator::generate);
      } else {
        for (int i = 0; i < ints.length; i++) {
          ints[i] = generator.generate(i);
        }
      }
    }
  }

  public static class TestClass {

    public static void main(String[] args) {
      int[] ints = new int[3];
      MyArrays1.setAll(ints, x -> x + 1);
      System.out.println(Arrays.toString(ints));
      MyArrays2.setAll(ints, x -> x + 2);
      System.out.println(Arrays.toString(ints));
    }
  }
}
