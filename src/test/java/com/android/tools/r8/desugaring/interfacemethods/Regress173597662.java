// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugaring.interfacemethods;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Regress173597662 extends DesugaredLibraryTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withDexRuntimes().withAllApiLevels().build());
  }

  public Regress173597662(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    Path path =
        testForD8(Backend.CF)
            .addProgramClasses(TestClass.class)
            .setMinApi(parameters.getApiLevel())
            .enableCoreLibraryDesugaring(parameters.getApiLevel())
            .addOptionsModification(
                o -> {
                  o.desugaredLibraryConfiguration = configurationAlternative3(o, false, parameters);
                })
            .compile()
            .writeToZip();
    Path pathSecond =
        testForD8(Backend.CF)
            .setMinApi(parameters.getApiLevel())
            .addProgramFiles(path)
            .enableCoreLibraryDesugaring(parameters.getApiLevel())
            .addOptionsModification(
                o -> {
                  o.desugarSpecificOptions().allowAllDesugaredInput = true;
                  o.desugaredLibraryConfiguration = configurationAlternative3(o, false, parameters);
                })
            .setMinApi(parameters.getApiLevel())
            .compile()
            .inspect(
                (inspector) -> {
                  MethodSubject forEach =
                      inspector.method(TestClass.class.getMethod("forEach", Consumer.class));
                  assertTrue(
                      parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N)
                          || forEach.isPresent() // TODO(b/173597662): this should not be present.
                      );
                })
            .writeToZip();

    testForD8(parameters.getBackend())
        .setMinApi(parameters.getApiLevel())
        .addProgramFiles(pathSecond)
        .disableDesugaring()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .addDesugaredCoreLibraryRunClassPath(this::buildDesugaredLibrary, parameters.getApiLevel())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("41", "42", "43");
  }

  static class TestClass implements Iterable<String> {

    public static void main(String[] args) {
      new TestClass().forEach(System.out::println);
    }

    @NotNull
    @Override
    public Iterator<String> iterator() {
      return null;
    }

    @Override
    public void forEach(Consumer<? super String> action) {
      action.accept("41");
      action.accept("42");
      action.accept("43");
    }

    @Override
    public Spliterator<String> spliterator() {
      return null;
    }
  }
}
