// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
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
  private final boolean shrinkDesugaredLibrary;

  @Parameters(name = "{1}, shrinkDesugaredLibrary: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withDexRuntimes().withAllApiLevels().build());
  }

  public CustomCollectionForwardingTest(boolean shrinkDesugaredLibrary, TestParameters parameters) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  @Test
  public void testCustomCollectionD8() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8()
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addInnerClasses(CustomCollectionForwardingTest.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(
            StringUtils.lines("false", "false", "false", "false", "false", "false"));
  }

  @Test
  public void testCustomCollectionR8() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForR8(parameters.getBackend())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addInnerClasses(CustomCollectionForwardingTest.class)
        .addKeepMainRule(Executor.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
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
