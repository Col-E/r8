// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NoForwardingMethodsTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getJdk8Jdk11(),
        DEFAULT_SPECIFICATIONS);
  }

  public NoForwardingMethodsTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void testNoForwardingMethods() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .addKeepClassAndMembersRules(Executor.class)
        .compile()
        .inspect(this::assertNoForwardingStreamMethod)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutputLines("str:1", "0");
  }

  private void assertNoForwardingStreamMethod(CodeInspector inspector) {
    assertTrue(
        inspector.clazz(CustomArrayList.class).uniqueMethodWithOriginalName("stream").isAbsent());
    assertTrue(
        inspector.clazz(CustomSortedSet.class).uniqueMethodWithOriginalName("stream").isAbsent());
  }

  static class Executor {

    // The main method is using stream, but since there are no overrides, the classes should not
    // have any forwarding method.
    public static void main(String[] args) {
      ArrayList<Object> cArrayList = new CustomArrayList<>();
      SortedSet<Object> cSortedSet = new CustomSortedSet<>();
      cArrayList.add(1);
      cSortedSet.add(1);
      System.out.println(cArrayList.stream().map(i -> "str:" + i).toArray()[0]);
      System.out.println(cSortedSet.stream().filter(Objects::isNull).count());
    }
  }

  // Extends directly a core library class which implements other library interfaces.
  private static class CustomArrayList<E> extends ArrayList<E> {}

  // Implements directly a core library interface which implements other library interfaces.
  static class CustomSortedSet<E> implements SortedSet<E> {

    @Override
    public Comparator<? super E> comparator() {
      return null;
    }

    @Override
    public SortedSet<E> subSet(E fromElement, E toElement) {
      return new CustomSortedSet<>();
    }

    @Override
    public SortedSet<E> headSet(E toElement) {
      return new CustomSortedSet<>();
    }

    @Override
    public SortedSet<E> tailSet(E fromElement) {
      return new CustomSortedSet<>();
    }

    @Override
    public E first() {
      return null;
    }

    @Override
    public E last() {
      return null;
    }

    @Override
    public int size() {
      return 0;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public boolean contains(Object o) {
      return false;
    }

    @Override
    public Iterator<E> iterator() {
      return Collections.emptyIterator();
    }

    @Override
    public Object[] toArray() {
      return new Object[0];
    }

    @Override
    public <T> T[] toArray(T[] a) {
      return a;
    }

    @Override
    public boolean add(Object o) {
      return false;
    }

    @Override
    public boolean remove(Object o) {
      return false;
    }

    @Override
    public boolean addAll(Collection c) {
      return false;
    }

    @Override
    public void clear() {}

    @Override
    public boolean removeAll(Collection c) {
      return false;
    }

    @Override
    public boolean retainAll(Collection c) {
      return false;
    }

    @Override
    public boolean containsAll(Collection c) {
      return false;
    }
  }
}
