// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static junit.framework.TestCase.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Spliterator;
import org.jetbrains.annotations.NotNull;
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
    expectThrowsWithHorizontalClassMergingIf(
        shrinkDesugaredLibrary
            && parameters.getApiLevel().isLessThan(AndroidApiLevel.N)
            && !parameters.getDexRuntimeVersion().isDefault());
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8()
        .addInnerClasses(CustomCollectionForwardingTest.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .inspect(this::assertForwardingMethods)
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

  private void assertForwardingMethods(CodeInspector inspector) {
    if (parameters.getApiLevel().getLevel() >= AndroidApiLevel.N.getLevel()) {
      return;
    }
    ClassSubject cal = inspector.clazz(CustomArrayList.class);
    MethodSubject spliteratorCal = cal.method("j$.util.Spliterator", "spliterator");
    assertTrue(spliteratorCal.isPresent());
    assertTrue(
        spliteratorCal
            .streamInstructions()
            .anyMatch(i -> i.isInvokeStatic() && i.toString().contains("List$-CC")));
    MethodSubject streamCal = cal.method("j$.util.stream.Stream", "stream");
    assertTrue(streamCal.isPresent());
    assertTrue(
        streamCal
            .streamInstructions()
            .anyMatch(i -> i.isInvokeStatic() && i.toString().contains("Collection$-CC")));

    ClassSubject clhs = inspector.clazz(CustomLinkedHashSet.class);
    MethodSubject spliteratorClhs = clhs.method("j$.util.Spliterator", "spliterator");
    assertTrue(spliteratorClhs.isPresent());
    assertTrue(
        spliteratorClhs
            .streamInstructions()
            .anyMatch(i -> i.isInvokeStatic() && i.toString().contains("DesugarLinkedHashSet")));
    MethodSubject streamClhs = clhs.method("j$.util.stream.Stream", "stream");
    assertTrue(streamClhs.isPresent());
    assertTrue(
        streamClhs
            .streamInstructions()
            .anyMatch(i -> i.isInvokeStatic() && i.toString().contains("Collection$-CC")));

    ClassSubject cl = inspector.clazz(CustomList.class);
    MethodSubject spliteratorCl = cl.method("j$.util.Spliterator", "spliterator");
    assertTrue(spliteratorCl.isPresent());
    assertTrue(
        spliteratorCl
            .streamInstructions()
            .anyMatch(i -> i.isInvokeStatic() && i.toString().contains("List$-CC")));
    MethodSubject streamCl = cl.method("j$.util.stream.Stream", "stream");
    assertTrue(streamCl.isPresent());
    assertTrue(
        streamCl
            .streamInstructions()
            .anyMatch(i -> i.isInvokeStatic() && i.toString().contains("Collection$-CC")));
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

    @NotNull
    @Override
    public Iterator<E> iterator() {
      return null;
    }

    @NotNull
    @Override
    public Object[] toArray() {
      return new Object[0];
    }

    @NotNull
    @Override
    public <T> T[] toArray(@NotNull T[] a) {
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
    public boolean containsAll(@NotNull Collection<?> c) {
      return false;
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends E> c) {
      return false;
    }

    @Override
    public boolean addAll(int index, @NotNull Collection<? extends E> c) {
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

    @NotNull
    @Override
    public ListIterator<E> listIterator() {
      return null;
    }

    @NotNull
    @Override
    public ListIterator<E> listIterator(int index) {
      return null;
    }

    @NotNull
    @Override
    public List<E> subList(int fromIndex, int toIndex) {
      return null;
    }
  }
}
