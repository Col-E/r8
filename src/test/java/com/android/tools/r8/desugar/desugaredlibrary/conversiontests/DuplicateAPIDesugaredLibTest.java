// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static junit.framework.TestCase.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.CustomLibrarySpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DuplicateAPIDesugaredLibTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;
  private final boolean supportCallbacks;

  private static final AndroidApiLevel MIN_SUPPORTED = AndroidApiLevel.O;
  private static final String EXPECTED_RESULT = StringUtils.lines(" 1 1.1", " 1 1.1");

  @Parameters(name = "{0}, spec: {1}, {2}, callbacks: {3}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withDexRuntimesStartingFromIncluding(Version.V8_1_0)
            .withApiLevel(AndroidApiLevel.B)
            .build(),
        getJdk8Jdk11(),
        ImmutableList.of(D8_L8DEBUG),
        BooleanUtils.values());
  }

  public DuplicateAPIDesugaredLibTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification,
      boolean supportCallbacks) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
    this.supportCallbacks = supportCallbacks;
  }

  @Test
  public void testLib() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramClasses(Executor.class)
        .setCustomLibrarySpecification(
            new CustomLibrarySpecification(CustomLibClass.class, MIN_SUPPORTED))
        .addKeepMainRule(Executor.class)
        .supportAllCallbacksFromLibrary(supportCallbacks)
        .compile()
        .inspectL8(this::assertDupMethod)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  private void assertDupMethod(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz("j$.util.concurrent.ConcurrentHashMap");
    int numForEachMethods =
        libraryDesugaringSpecification == JDK8
            ? supportCallbacks ? 2 : 1
            : supportCallbacks ? 4 : 3;
    assertEquals(
        numForEachMethods,
        clazz.virtualMethods().stream().filter(m -> m.getOriginalName().equals("forEach")).count());
  }

  static class Executor {

    public static void main(String[] args) {
      Map<Integer, Double> map = new ConcurrentHashMap<>();
      map.put(1, 1.1);
      BiConsumer<Integer, Double> biConsumer = (x, y) -> System.out.print(" " + x + " " + y);
      map.forEach(biConsumer);
      System.out.println();
      CustomLibClass.javaForEach(map, biConsumer);
      System.out.println();
    }
  }

  // This class will be put at compilation time as library and on the runtime class path.
  // This class is convenient for easy testing. Each method plays the role of methods in the
  // platform APIs for which argument/return values need conversion.
  static class CustomLibClass {

    @SuppressWarnings("WeakerAccess")
    public static <K, V> void javaForEach(Map<K, V> map, BiConsumer<K, V> consumer) {
      map.forEach(consumer);
    }
  }
}
