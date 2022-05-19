// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8CF2CF_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8SHRINK;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static junit.framework.TestCase.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IteratorTest extends DesugaredLibraryTestBase {

  private static final String EXPECTED_OUTPUT = StringUtils.lines("1", "2", "3");

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;
  private final boolean canUseDefaultAndStaticInterfaceMethods;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build(),
        getJdk8Jdk11(),
        ImmutableList.of(D8_L8DEBUG, D8_L8SHRINK, D8CF2CF_L8DEBUG));
  }

  public IteratorTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
    this.canUseDefaultAndStaticInterfaceMethods =
        parameters
            .getApiLevel()
            .isGreaterThanOrEqualTo(apiLevelWithDefaultInterfaceMethodsSupport());
  }

  @Test
  public void testIterator() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .compile()
        .inspect(this::assertInterface)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private void assertInterface(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(MyIterator.class);
    assertEquals(
        canUseDefaultAndStaticInterfaceMethods ? 0 : 1,
        clazz.getDexProgramClass().getInterfaces().stream()
            .filter(name -> name.toString().equals("j$.util.Iterator"))
            .count());
    assertEquals(
        canUseDefaultAndStaticInterfaceMethods ? 1 : 2,
        clazz.getDexProgramClass().allMethodsSorted().stream()
            .filter(m -> m.getReference().getName().toString().equals("forEachRemaining"))
            .count());
  }

  static class Main {

    public static void main(String[] args) {
      Iterator<Integer> iterator = new MyIterator<>(1, 2, 3);
      iterator.forEachRemaining(System.out::println);
    }
  }

  static class MyIterator<E> implements Iterator<E> {

    int index;
    E[] items;

    @SafeVarargs
    public MyIterator(E... items) {
      this.items = items;
    }

    @Override
    public boolean hasNext() {
      return index < items.length;
    }

    @Override
    public E next() {
      return items[index++];
    }

    @Override
    public void forEachRemaining(Consumer<? super E> action) {
      while (hasNext()) {
        action.accept(next());
      }
    }
  }
}
