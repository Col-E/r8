// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import static junit.framework.TestCase.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.DexRuntime;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DuplicateAPIDesugaredLibTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;
  private static final AndroidApiLevel MIN_SUPPORTED = AndroidApiLevel.N;

  @Parameterized.Parameters(name = "{0}, shrinkDesugaredLibrary: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getConversionParametersUpToExcluding(MIN_SUPPORTED), BooleanUtils.values());
  }

  public DuplicateAPIDesugaredLibTest(TestParameters parameters, boolean shrinkDesugaredLibrary) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  @Test
  public void testLib() throws Exception {
    for (Boolean supportAllCallbacksFromLibrary : BooleanUtils.values()) {
      Box<Path> desugaredLibBox = new Box<>();
      Path customLib =
          testForD8()
              .addProgramClasses(CustomLibClass.class)
              .setMinApi(AndroidApiLevel.B)
              .compile()
              .writeToZip();
      String stdOut =
          testForD8()
              .setMinApi(AndroidApiLevel.B)
              .addProgramClasses(Executor.class)
              .addLibraryClasses(CustomLibClass.class)
              .enableCoreLibraryDesugaring(AndroidApiLevel.B)
              .compile()
              .addDesugaredCoreLibraryRunClassPath(
                  (AndroidApiLevel api) -> {
                    desugaredLibBox.set(
                        this.buildDesugaredLibrary(
                            api,
                            opt ->
                                opt.desugaredLibraryConfiguration =
                                    configurationWithSupportAllCallbacksFromLibrary(
                                        opt, true, parameters, supportAllCallbacksFromLibrary)));
                    return desugaredLibBox.get();
                  },
                  AndroidApiLevel.B)
              .addRunClasspathFiles(customLib)
              .run(new DexRuntime(DexVm.ART_9_0_0_HOST), Executor.class)
              .assertSuccess()
              .getStdOut();
      assertDupMethod(new CodeInspector(desugaredLibBox.get()), supportAllCallbacksFromLibrary);
      assertLines2By2Correct(stdOut);
    }
  }

  private void assertDupMethod(CodeInspector inspector, boolean supportAllCallbacksFromLibrary) {
    ClassSubject clazz = inspector.clazz("j$.util.concurrent.ConcurrentHashMap");
    assertEquals(
        supportAllCallbacksFromLibrary ? 2 : 1,
        clazz.virtualMethods().stream().filter(m -> m.getOriginalName().equals("forEach")).count());
  }

  static class Executor {

    public static void main(String[] args) {
      Map<Integer, Double> map = new ConcurrentHashMap<>();
      map.put(1, 1.1);
      map.put(2, 2.2);
      BiConsumer<Integer, Double> biConsumer = (x, y) -> System.out.print(" " + x + " " + y);
      map.forEach(biConsumer);
      System.out.println();
      CustomLibClass.javaForEach(map, biConsumer);
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
