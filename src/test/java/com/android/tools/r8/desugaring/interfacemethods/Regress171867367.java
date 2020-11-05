// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugaring.interfacemethods;

import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Regress171867367 extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withDexRuntimes().withAllApiLevels().build());
  }

  public Regress171867367(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    Path path =
        testForD8(Backend.CF)
            .addProgramClasses(TestClass.class)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .writeToZip();
    D8TestBuilder d8TestBuilder =
        testForD8(parameters.getBackend())
            .addProgramFiles(path)
            .addAndroidBuildVersion()
            .addProgramClasses(TestClass2.class)
            .setMinApi(parameters.getApiLevel());
    if (parameters.getApiLevel().isLessThan(AndroidApiLevel.N)) {
      d8TestBuilder
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutputLines("42", "45", "43");
    } else {
      d8TestBuilder
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutputLines("42", "43", "45");
    }
  }

  static class TestClass {
    public static void main(String[] args) {
      List<Integer> values = new ArrayList<>();
      values.add(42);
      values.add(45);
      values.add(43);
      if (AndroidBuildVersion.VERSION >= 24) {
        // Comparator.nulls{First,Last} are static interface methods, will trigger the DC class.
        Collections.sort(
            values,
            Comparator.nullsFirst(
                (a, b) -> {
                  return Integer.compare(a, b);
                }));
        Collections.sort(
            values,
            Comparator.nullsLast(
                (a, b) -> {
                  return Integer.compare(a, b);
                }));
      }
      for (Integer value : values) {
        System.out.println(value);
      }
    }
  }

  static class TestClass2 {
    public static void foo() {
      List<Integer> values = new ArrayList<>();
      values.add(142);
      values.add(145);
      values.add(143);
      if (AndroidBuildVersion.VERSION >= 24) {
        Collections.sort(
            values,
            Comparator.nullsFirst(
                (a, b) -> {
                  return Integer.compare(a, b);
                }));
      }
    }
  }
}
