// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import static junit.framework.TestCase.assertEquals;

import com.android.tools.r8.TestRuntime.DexRuntime;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import org.junit.Test;

public class DuplicateAPIProgramTest extends APIConversionTestBase {

  @Test
  public void testMap() throws Exception {
    Path customLib = testForD8().addProgramClasses(CustomLibClass.class).compile().writeToZip();
    String stdOut =
        testForD8()
            .setMinApi(AndroidApiLevel.B)
            .addProgramClasses(Executor.class, MyMap.class)
            .addLibraryClasses(CustomLibClass.class)
            .enableCoreLibraryDesugaring(AndroidApiLevel.B)
            .compile()
            .inspect(this::assertDupMethod)
            .addDesugaredCoreLibraryRunClassPath(
                this::buildDesugaredLibraryWithConversionExtension, AndroidApiLevel.B)
            .addRunClasspathFiles(customLib)
            .run(new DexRuntime(DexVm.ART_9_0_0_HOST), Executor.class)
            .assertSuccess()
            .getStdOut();
    assertLines2By2Correct(stdOut);
  }

  private void assertDupMethod(CodeInspector i) {
    assertEquals(
        2,
        i.clazz(MyMap.class).virtualMethods().stream()
            .filter(m -> m.getOriginalName().equals("forEach"))
            .count());
  }

  static class Executor {

    public static void main(String[] args) {
      IdentityHashMap<Integer, Double> map = new MyMap<>();
      map.put(1, 1.1);
      map.put(2, 2.2);
      BiConsumer<Integer, Double> biConsumer = (x, y) -> System.out.print(" " + x + " " + y);
      map.forEach(biConsumer);
      System.out.println();
      CustomLibClass.javaForEach(map, biConsumer);
    }
  }

  public static class MyMap<K, V> extends IdentityHashMap<K, V> {

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
      System.out.print("ForEach");
      super.forEach(action);
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
