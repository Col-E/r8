// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.StringUtils;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CustomCollectionInterfaceSuperTest extends DesugaredLibraryTestBase {

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "removeIf from MyCol1",
          "removeIf from MyCol1",
          "removeIf from MyCol2",
          "removeIf from MyCol1",
          "removeIf from MyCol2",
          "removeIf from MyCol1");

  private final TestParameters parameters;
  private final CompilationSpecification compilationSpecification;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getJdk8Jdk11(),
        DEFAULT_SPECIFICATIONS);
  }

  public CustomCollectionInterfaceSuperTest(
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
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  static class Main {

    @SuppressWarnings({
      "MismatchedQueryAndUpdateOfCollection",
      "RedundantOperationOnEmptyContainer"
    })
    public static void main(String[] args) {
      Col<Integer> ints = new Col<>();
      Col1<Integer> ints1 = new Col1<>();
      Col2<Integer> ints2 = new Col2<>();
      ints.removeIf(x -> x == 1);
      ints.superRemoveIf(x -> x == 2);
      ints1.removeIf(x -> x == 3);
      ints1.superRemoveIf(x -> x == 4);
      ints2.removeIf(x -> x == 5);
      ints2.superRemoveIf(x -> x == 6);
    }
  }

  interface Col1Itf<E> extends Collection<E> {

    @Override
    default boolean removeIf(Predicate<? super E> filter) {
      System.out.println("removeIf from MyCol1");
      return Collection.super.removeIf(filter);
    }
  }

  interface Col2Itf<E> extends Col1Itf<E> {

    @Override
    default boolean removeIf(Predicate<? super E> filter) {
      System.out.println("removeIf from MyCol2");
      return Col1Itf.super.removeIf(filter);
    }
  }

  static class Col<E> implements Collection<E> {

    public boolean superRemoveIf(Predicate<? super E> filter) {
      return Collection.super.removeIf(filter);
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
    public boolean removeAll(Collection<?> c) {
      return false;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
      return false;
    }

    @Override
    public void clear() {}
  }

  static class Col1<E> implements Col1Itf<E> {

    public boolean superRemoveIf(Predicate<? super E> filter) {
      return Col1Itf.super.removeIf(filter);
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
    public boolean removeAll(Collection<?> c) {
      return false;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
      return false;
    }

    @Override
    public void clear() {}
  }

  static class Col2<E> implements Col2Itf<E> {

    public boolean superRemoveIf(Predicate<? super E> filter) {
      return Col2Itf.super.removeIf(filter);
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
    public boolean removeAll(Collection<?> c) {
      return false;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
      return false;
    }

    @Override
    public void clear() {}
  }
}
