// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.ir.desugar.DesugaredLibraryWrapperSynthesizer;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class WrapperMergeTest extends DesugaredLibraryTestBase {

  private static final String EXPECTED = StringUtils.lines("[1, 2, 3]", "[2, 3, 4]");

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private final TestParameters parameters;

  public WrapperMergeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addAndroidBuildVersion()
        .addProgramClassesAndInnerClasses(MyArrays1.class)
        .addProgramClassesAndInnerClasses(MyArrays2.class)
        .addProgramClasses(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testWrapperMerge() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    // Multiple wrapper classes have to be merged here.
    Path path1 = compileWithCoreLibraryDesugaring(MyArrays1.class);
    Path path2 = compileWithCoreLibraryDesugaring(MyArrays2.class);
    testForD8()
        .addProgramFiles(path1, path2)
        .addProgramClasses(TestClass.class)
        .addAndroidBuildVersion()
        .enableCoreLibraryDesugaring(parameters.getApiLevel())
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::assertWrappers)
        .inspect(this::assertNoDuplicates)
        .addDesugaredCoreLibraryRunClassPath(this::buildDesugaredLibrary, parameters.getApiLevel())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  private Path compileWithCoreLibraryDesugaring(Class<?> clazz) throws Exception {
    return testForD8()
        .addProgramClassesAndInnerClasses(clazz)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel())
        .compile()
        .inspect(this::assertWrappers)
        .writeToZip();
  }

  private void assertNoDuplicates(CodeInspector inspector) {
    Object2ReferenceMap<String, Set<FoundClassSubject>> map = new Object2ReferenceOpenHashMap<>();
    for (FoundClassSubject clazz : inspector.allClasses()) {
      map.computeIfAbsent(clazz.getFinalName(), k -> Sets.newIdentityHashSet()).add(clazz);
    }
    for (Set<FoundClassSubject> duplicates : map.values()) {
      if (duplicates.size() > 1) {
        fail("Unexpected duplicates: " + duplicates);
      }
    }
  }

  private boolean hasNativeIntUnaryOperator() {
    return parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N);
  }

  private void assertWrappers(CodeInspector inspector) {
    assertEquals(
        hasNativeIntUnaryOperator() ? 0 : 2,
        inspector.allClasses().stream()
            .filter(
                c ->
                    c.getOriginalName().contains(DesugaredLibraryWrapperSynthesizer.WRAPPER_PREFIX))
            .count());
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
