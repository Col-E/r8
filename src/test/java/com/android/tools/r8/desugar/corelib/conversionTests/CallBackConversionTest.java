// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib.conversionTests;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import com.android.tools.r8.TestRuntime.DexRuntime;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;

public class CallBackConversionTest extends APIConversionTestBase {

  @Test
  public void testCallBack() throws Exception {
    Path customLib = testForD8().addProgramClasses(CustomLibClass.class).compile().writeToZip();
    testForD8()
        .setMinApi(AndroidApiLevel.B)
        .addProgramClasses(Impl.class)
        .addLibraryClasses(CustomLibClass.class)
        .enableCoreLibraryDesugaring(AndroidApiLevel.B)
        .compile()
        .inspect(
            i -> {
              // foo(j$) and foo(java)
              List<FoundMethodSubject> virtualMethods = i.clazz(Impl.class).virtualMethods();
              assertEquals(2, virtualMethods.size());
              assertTrue(
                  virtualMethods.stream()
                      .anyMatch(
                          m ->
                              m.getMethod()
                                  .method
                                  .proto
                                  .parameters
                                  .values[0]
                                  .toString()
                                  .equals("j$.util.function.Consumer")));
              assertTrue(
                  virtualMethods.stream()
                      .anyMatch(
                          m ->
                              m.getMethod()
                                  .method
                                  .proto
                                  .parameters
                                  .values[0]
                                  .toString()
                                  .equals("java.util.function.Consumer")));
            })
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibraryWithConversionExtension, AndroidApiLevel.B)
        .addRunClasspathFiles(customLib)
        .run(new DexRuntime(DexVm.ART_9_0_0_HOST), Impl.class)
        .assertSuccessWithOutput(StringUtils.lines("0", "1", "0", "1"));
  }

  static class Impl extends CustomLibClass {

    public int foo(Consumer<Object> o) {
      o.accept(0);
      return 1;
    }

    public static void main(String[] args) {
      Impl impl = new Impl();
      // Call foo through java parameter.
      System.out.println(CustomLibClass.callFoo(impl, System.out::println));
      // Call foo through j$ parameter.
      System.out.println(impl.foo(System.out::println));
    }
  }

  abstract static class CustomLibClass {

    public abstract int foo(Consumer<Object> consumer);

    @SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
    public static int callFoo(CustomLibClass object, Consumer<Object> consumer) {
      return object.foo(consumer);
    }
  }
}
