// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NoDesugaredLibraryDexFileTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;

  @Parameters(name = "{1}, shrinkDesugaredLibrary: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withDexRuntimes().withAllApiLevels().build());
  }

  public NoDesugaredLibraryDexFileTest(boolean shrinkDesugaredLibrary, TestParameters parameters) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  @Test
  public void testCustomCollectionD8() throws Exception {
    Assume.assumeTrue(requiresEmulatedInterfaceCoreLibDesugaring(parameters));
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8()
        .addInnerClasses(NoDesugaredLibraryDexFileTest.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .inspect(this::assertNoForwardingStreamMethod)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutputLines("1", "0");
    assertTrue(keepRuleConsumer.get().isEmpty());
  }

  @Test
  public void testCustomCollectionR8() throws Exception {
    Assume.assumeTrue(requiresEmulatedInterfaceCoreLibDesugaring(parameters));
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForR8(parameters.getBackend())
        .addInnerClasses(NoDesugaredLibraryDexFileTest.class)
        .setMinApi(parameters.getApiLevel())
        .addKeepClassAndMembersRules(Executor.class)
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .inspect(this::assertNoForwardingStreamMethod)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutputLines("1", "0");
    assertTrue(keepRuleConsumer.get().isEmpty());
  }

  private void assertNoForwardingStreamMethod(CodeInspector inspector) {
    assertTrue(inspector.clazz(CustomArrayList.class).uniqueMethodWithName("stream").isAbsent());
    assertTrue(inspector.clazz(CustomSortedSet.class).uniqueMethodWithName("stream").isAbsent());
  }

  static class Executor {

    // No method here is using any emulated interface default method, so, there is no need for
    // the desugared library dex file despite desugared library being enabled.
    public static void main(String[] args) {
      ArrayList<Object> cArrayList = new CustomArrayList<>();
      SortedSet<Object> cSortedSet = new CustomSortedSet<>();
      cArrayList.add(1);
      cSortedSet.add(1);
      System.out.println(cArrayList.size());
      System.out.println(cSortedSet.size());
    }
  }

  // Extends directly a core library class which implements other library interfaces.
  private static class CustomArrayList<E> extends ArrayList<E> {}

  // Implements directly a core library interface which implements other library interfaces.
  static class CustomSortedSet<E> implements SortedSet<E> {

    @Nullable
    @Override
    public Comparator<? super E> comparator() {
      return null;
    }

    @NotNull
    @Override
    public SortedSet<E> subSet(E fromElement, E toElement) {
      return new CustomSortedSet<>();
    }

    @NotNull
    @Override
    public SortedSet<E> headSet(E toElement) {
      return new CustomSortedSet<>();
    }

    @NotNull
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
    public boolean add(Object o) {
      return false;
    }

    @Override
    public boolean remove(Object o) {
      return false;
    }

    @Override
    public boolean addAll(@NotNull Collection c) {
      return false;
    }

    @Override
    public void clear() {}

    @Override
    public boolean removeAll(@NotNull Collection c) {
      return false;
    }

    @Override
    public boolean retainAll(@NotNull Collection c) {
      return false;
    }

    @Override
    public boolean containsAll(@NotNull Collection c) {
      return false;
    }
  }
}
