// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.desugaredlibrary;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.Arrays;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DontKeepBootstrapClassesTest extends DesugaredLibraryTestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  final AndroidApiLevel minApiLevel = AndroidApiLevel.B;

  public DontKeepBootstrapClassesTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void test() throws Exception {
    KeepRuleConsumer keepRuleConsumer = new PresentKeepRuleConsumer();
    testForD8()
        .addProgramClasses(TestClass.class)
        .setMinApi(minApiLevel)
        .addLibraryClasses(CustomLibClass.class)
        .enableCoreLibraryDesugaring(minApiLevel, keepRuleConsumer)
        .compile();
    assertThat(keepRuleConsumer.get(), containsString("-keep class j$.util.function.Consumer"));
    // TODO(b/158635415): Don't generate keep rules targeting items outside desugared library.
    assertThat(keepRuleConsumer.get(), containsString("-keep class java.util"));
  }

  static class CustomLibClass {
    public static <T> Consumer<T> id(Consumer<T> fn) {
      return fn;
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      Arrays.asList("A", "B", "C").forEach(CustomLibClass.id(System.out::println));
    }
  }
}
