// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.SPECIFICATIONS_WITH_CF2CF;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.BufferedReader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BufferedReaderTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final CompilationSpecification compilationSpecification;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withAllRuntimes()
            .withAllApiLevelsAlsoForCf()
            .withApiLevel(AndroidApiLevel.N)
            .build(),
        ImmutableList.of(LibraryDesugaringSpecification.JDK11),
        SPECIFICATIONS_WITH_CF2CF);
  }

  public BufferedReaderTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  private String expectedOutput() {
    return StringUtils.lines("Hello", "Larry", "Page", "Caught java.io.UncheckedIOException");
  }

  @Test
  public void test() throws Exception {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(BufferedReaderTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput());
  }

  static class TestClass {

    @NeverInline
    public static void testBufferedReaderLines() throws Exception {
      try (BufferedReader reader = new BufferedReader(new StringReader("Hello\nLarry\nPage"))) {
        reader.lines().forEach(System.out::println);
      }
    }

    @NeverInline
    public static void testBufferedReaderLines_uncheckedIoException() throws Exception {
      BufferedReader reader = new BufferedReader(new StringReader(""));
      reader.close();
      try {
        reader.lines().count();
        System.out.println("UncheckedIOException expected");
      } catch (UncheckedIOException expected) {
        System.out.println("Caught " + expected.getClass().getName());
      } catch (Throwable t) {
        System.out.println("Caught unexpected" + t.getClass().getName());
      }
    }

    public static void main(String[] args) throws Exception {
      testBufferedReaderLines();
      testBufferedReaderLines_uncheckedIoException();
    }
  }
}
