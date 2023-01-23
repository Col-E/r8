// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdk11;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InterfacePartialDesugTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        ImmutableList.of(JDK11_PATH),
        DEFAULT_SPECIFICATIONS);
  }

  public InterfacePartialDesugTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void test() throws Exception {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramClassFileData(getTransforms())
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("42", "0");
  }

  private List<byte[]> getTransforms() throws IOException, NoSuchMethodException {
    return ImmutableList.of(
        transformer(Itf.class)
            .setAccessFlags(
                Itf.class.getDeclaredMethod("privateRun", Supplier.class),
                flags -> {
                  flags.unsetPublic();
                  flags.setPrivate();
                })
            .setAccessFlags(
                Itf.class.getDeclaredMethod("privateGet"),
                flags -> {
                  flags.unsetPublic();
                  flags.setPrivate();
                })
            .transform());
  }

  public static class Main implements Itf {

    public static void main(String[] args) {
      System.out.println(new Main().get());
      // We need to check a call to clone is correctly non desugared.
      System.out.println((new Main[0]).clone().length);
    }
  }

  public interface Itf {

    default Object get() {
      return (privateRun(this::privateGet));
    }

    // Method will be private at runtime.
    default Object privateRun(Supplier<Object> getter) {
      return getter.get();
    }

    // Method will be private at runtime.
    default Object privateGet() {
      return 42;
    }
  }
}
