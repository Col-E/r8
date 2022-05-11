// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.SPECIFICATIONS_WITH_CF2CF;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.StringUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Spliterator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CustomCollectionForwardingTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final CompilationSpecification compilationSpecification;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getJdk8Jdk11(),
        SPECIFICATIONS_WITH_CF2CF);
  }

  public CustomCollectionForwardingTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  @Test
  public void testCollection() throws Exception {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .addKeepMainRule(Executor.class)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(
            StringUtils.lines("false", "false", "false", "false", "false", "false"));
  }

  static class Executor {

    public static void main(String[] args) {
      CustomArrayList<Object> objects = new CustomArrayList<>();
      System.out.println(objects.spliterator().hasCharacteristics(Spliterator.CONCURRENT));
      System.out.println(objects.stream().spliterator().hasCharacteristics(Spliterator.CONCURRENT));

      CustomLinkedHashSet<Object> objects2 = new CustomLinkedHashSet<>();
      System.out.println(objects2.spliterator().hasCharacteristics(Spliterator.CONCURRENT));
      System.out.println(
          objects2.stream().spliterator().hasCharacteristics(Spliterator.CONCURRENT));

      CustomList<Object> objects3 = new CustomList<>();
      System.out.println(objects3.spliterator().hasCharacteristics(Spliterator.CONCURRENT));
      System.out.println(
          objects3.stream().spliterator().hasCharacteristics(Spliterator.CONCURRENT));
    }
  }

  static class CustomArrayList<E> extends ArrayList<E> implements Collection<E> {}

  static class CustomLinkedHashSet<E> extends LinkedHashSet<E> implements Collection<E> {}

  static class CustomList<E> implements Collection<E>, List<E> {

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
      return null;
    }

    @Override
    public Object[] toArray() {
      return new Object[0];
    }

    @Override
    public <T> T[] toArray(T[] a) {
      return null;
    }

    @Override
    public boolean add(E e) {
      return false;
    }

    @Override
    public boolean remove(Object o) {
      return false;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
      return false;
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
      return false;
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
      return false;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
      return false;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
      return false;
    }

    @Override
    public void clear() {}

    @Override
    public E get(int index) {
      return null;
    }

    @Override
    public E set(int index, E element) {
      return null;
    }

    @Override
    public void add(int index, E element) {}

    @Override
    public E remove(int index) {
      return null;
    }

    @Override
    public int indexOf(Object o) {
      return 0;
    }

    @Override
    public int lastIndexOf(Object o) {
      return 0;
    }

    @Override
    public ListIterator<E> listIterator() {
      return null;
    }

    @Override
    public ListIterator<E> listIterator(int index) {
      return null;
    }

    @Override
    public List<E> subList(int fromIndex, int toIndex) {
      return null;
    }
  }
}
