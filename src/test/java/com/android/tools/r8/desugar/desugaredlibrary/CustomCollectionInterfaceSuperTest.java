// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CustomCollectionInterfaceSuperTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;

  @Parameters(name = "{1}, shrinkDesugaredLibrary: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimes().withAllApiLevels().build());
  }

  public CustomCollectionInterfaceSuperTest(
      boolean shrinkDesugaredLibrary, TestParameters parameters) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "removeIf from MyCol1",
          "removeIf from MyCol1",
          "removeIf from MyCol2",
          "removeIf from MyCol1",
          "removeIf from MyCol2",
          "removeIf from MyCol1");

  @Test
  public void testCustomCollectionD8() throws Exception {
    if (parameters.isCfRuntime()) {
      testForJvm()
          .addInnerClasses(CustomCollectionInterfaceSuperTest.class)
          .run(parameters.getRuntime(), Main.class)
          .assertSuccessWithOutput(EXPECTED_OUTPUT);
      return;
    }
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8()
        .addInnerClasses(CustomCollectionInterfaceSuperTest.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .assertNoMessages()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testCustomCollectionR8() throws Exception {
    // Desugared library tests do not make sense in the Cf to Cf, and the JVM is already tested
    // in the D8 test. Just return.
    assumeTrue(parameters.isDexRuntime());
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForR8(parameters.getBackend())
        .addInnerClasses(CustomCollectionInterfaceSuperTest.class)
        .addKeepMainRule(Main.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .assertNoMessages()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
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

    @NotNull
    @Override
    public Iterator<E> iterator() {
      return Collections.emptyIterator();
    }

    @NotNull
    @Override
    public Object[] toArray() {
      return new Object[0];
    }

    @NotNull
    @Override
    public <T> T[] toArray(@NotNull T[] a) {
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
    public boolean containsAll(@NotNull Collection<?> c) {
      return false;
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends E> c) {
      return false;
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> c) {
      return false;
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
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

    @NotNull
    @Override
    public Iterator<E> iterator() {
      return Collections.emptyIterator();
    }

    @NotNull
    @Override
    public Object[] toArray() {
      return new Object[0];
    }

    @NotNull
    @Override
    public <T> T[] toArray(@NotNull T[] a) {
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
    public boolean containsAll(@NotNull Collection<?> c) {
      return false;
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends E> c) {
      return false;
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> c) {
      return false;
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
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

    @NotNull
    @Override
    public Iterator<E> iterator() {
      return Collections.emptyIterator();
    }

    @NotNull
    @Override
    public Object[] toArray() {
      return new Object[0];
    }

    @NotNull
    @Override
    public <T> T[] toArray(@NotNull T[] a) {
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
    public boolean containsAll(@NotNull Collection<?> c) {
      return false;
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends E> c) {
      return false;
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> c) {
      return false;
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
      return false;
    }

    @Override
    public void clear() {}
  }
}
