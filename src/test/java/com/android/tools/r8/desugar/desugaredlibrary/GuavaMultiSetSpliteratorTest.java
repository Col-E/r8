// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8SHRINK;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.R8_L8SHRINK;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DaggerUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Multiset;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;
import javax.annotation.Nonnull;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GuavaMultiSetSpliteratorTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withDexRuntimes()
            .withApiLevel(AndroidApiLevel.L)
            .withAllApiLevels()
            .build(),
        getJdk8Jdk11(),
        ImmutableList.of(D8_L8SHRINK, R8_L8SHRINK));
  }

  public GuavaMultiSetSpliteratorTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void testGuava() throws Throwable {
    if (!compilationSpecification.isProgramShrink()) {
      // We need multidex for non shrinking build.
      Assume.assumeTrue(parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.L));
    }
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        // TODO(b/289363570): Guava should not rely on dagger.
        .addProgramFiles(DaggerUtils.getGuavaFromDagger())
        .addInnerClasses(getClass())
        .addOptionsModification(opt -> opt.ignoreMissingClasses = true)
        .allowDiagnosticWarningMessages()
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("17744", "NullPointerException");
  }

  public static class Main {

    public static void main(String[] args) {
      System.out.println(ImmutableMultiset.of().spliterator().characteristics());
      try {
        System.out.println(new MyMultiSet<>().spliterator().characteristics());
      } catch (Exception e) {
        System.out.println(e.getClass().getSimpleName());
      }
    }
  }

  public static class MyMultiSet<E> implements Multiset<E> {

    @Override
    public int size() {
      return 0;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public int count(@Nullable Object element) {
      return 0;
    }

    @Override
    public int add(@Nullable E element, int occurrences) {
      return 0;
    }

    @Override
    public boolean add(E element) {
      return false;
    }

    @Override
    public int remove(@Nullable Object element, int occurrences) {
      return 0;
    }

    @Override
    public boolean remove(@Nullable Object element) {
      return false;
    }

    @Override
    public int setCount(E element, int count) {
      return 0;
    }

    @Override
    public boolean setCount(E element, int oldCount, int newCount) {
      return false;
    }

    @Override
    public Set<E> elementSet() {
      return null;
    }

    @Override
    public Set<Entry<E>> entrySet() {
      return null;
    }

    @Override
    public Iterator<E> iterator() {
      return null;
    }

    @Nonnull
    @Override
    public Object[] toArray() {
      return new Object[0];
    }

    @Nonnull
    @Override
    public <T> T[] toArray(@Nonnull T[] ts) {
      return null;
    }

    @Override
    public boolean contains(@Nullable Object element) {
      return false;
    }

    @Override
    public boolean containsAll(Collection<?> elements) {
      return false;
    }

    @Override
    public boolean addAll(@Nonnull Collection<? extends E> collection) {
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
