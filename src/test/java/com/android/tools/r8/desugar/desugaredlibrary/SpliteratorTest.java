// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static junit.framework.TestCase.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SpliteratorTest extends DesugaredLibraryTestBase {

  private static final String EXPECTED_OUTPUT_JDK11 =
      StringUtils.lines(
          "j$.util.AbstractList$RandomAccessSpliterator",
          "j$.util.AbstractList$RandomAccessSpliterator",
          "j$.util.Spliterators$IteratorSpliterator",
          "j$.util.Spliterators$IteratorSpliterator");
  private static final String EXPECTED_OUTPUT_JDK8 =
      StringUtils.lines(
          "j$.util.Spliterators$IteratorSpliterator",
          "j$.util.Spliterators$IteratorSpliterator",
          "j$.util.Spliterators$IteratorSpliterator",
          "j$.util.Spliterators$IteratorSpliterator");

  private final TestParameters parameters;
  private final CompilationSpecification compilationSpecification;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getJdk8Jdk11(),
        ImmutableList.of(D8_L8DEBUG));
  }

  public SpliteratorTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  @Test
  public void testSpliterator() throws Throwable {
    Assume.assumeTrue(requiresEmulatedInterfaceCoreLibDesugaring(parameters));
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .compile()
        .inspect(this::validateInterfaces)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(
            libraryDesugaringSpecification != JDK8 ? EXPECTED_OUTPUT_JDK11 : EXPECTED_OUTPUT_JDK8);
  }

  private void validateInterfaces(CodeInspector inspector) {
    assertTrue(
        inspector
            .clazz("com.android.tools.r8.desugar.desugaredlibrary.SpliteratorTest$MyArrayList")
            .getDexProgramClass()
            .interfaces
            .toString()
            .contains("j$.util.List"));
    assertTrue(
        inspector
            .clazz("com.android.tools.r8.desugar.desugaredlibrary.SpliteratorTest$MyLinkedList")
            .getDexProgramClass()
            .interfaces
            .toString()
            .contains("j$.util.List"));
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(new ArrayList<>().spliterator().getClass().getName());
      System.out.println(new MyArrayList<>().spliterator().getClass().getName());
      System.out.println(new LinkedList<>().spliterator().getClass().getName());
      System.out.println(new MyLinkedList<>().spliterator().getClass().getName());
    }
  }

  static class MyArrayList<E> extends ArrayList<E> {
    @Override
    public void sort(Comparator<? super E> c) {
      // Override to force j$ interface.
    }
  }

  static class MyLinkedList<E> extends LinkedList<E> {
    @Override
    public void sort(Comparator<? super E> c) {
      // Override to force j$ interface.
    }
  }
}
