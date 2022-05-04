// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import static com.android.tools.r8.CollectorsUtils.toSingle;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.CustomLibrarySpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConversionIntroduceInterfaceMethodTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;
  private final boolean supportAllCallbacksFromLibrary;

  private static final AndroidApiLevel MIN_SUPPORTED = AndroidApiLevel.N;
  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "action called from j$ consumer",
          "forEach called",
          "action called from java consumer",
          "forEach called");
  private static final String FAILING_EXPECTED_RESULT =
      StringUtils.lines(
          "action called from j$ consumer", "forEach called", "action called from java consumer");

  @Parameters(name = "{0}, spec: {1}, {2}, supportCallbacks: {3}")
  public static List<Object[]> data() {
    return buildParameters(
        getConversionParametersUpToExcluding(MIN_SUPPORTED),
        getJdk8Jdk11(),
        DEFAULT_SPECIFICATIONS,
        BooleanUtils.values());
  }

  public ConversionIntroduceInterfaceMethodTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification,
      boolean supportAllCallbacksFromLibrary) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
    this.supportAllCallbacksFromLibrary = supportAllCallbacksFromLibrary;
  }

  @Test
  public void testInterfaceMethod() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramClasses(MyCollectionInterface.class, MyCollection.class, Executor.class)
        .setCustomLibrarySpecification(
            new CustomLibrarySpecification(CustomLibClass.class, MIN_SUPPORTED))
        .addOptionsModification(opt -> opt.testing.trackDesugaredAPIConversions = true)
        .supportAllCallbacksFromLibrary(supportAllCallbacksFromLibrary)
        .addKeepMainRule(Executor.class)
        .allowDiagnosticWarningMessages()
        .compile()
        .inspect(this::assertDoubleForEach)
        .inspect(this::assertWrapperMethodsPresent)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(
            supportAllCallbacksFromLibrary ? EXPECTED_RESULT : FAILING_EXPECTED_RESULT);
  }

  private void assertDoubleForEach(CodeInspector inspector) {
    FoundClassSubject myCollection =
        inspector.allClasses().stream()
            .filter(
                c ->
                    c.getOriginalName()
                            .startsWith(
                                "com.android.tools.r8.desugar.desugaredlibrary.conversiontests")
                        && !c.getOriginalName().contains("Executor")
                        && !c.getOriginalName().contains("$-CC")
                        && !c.getDexProgramClass().isInterface())
            .collect(toSingle());
    assertEquals(
        "Missing duplicated forEach",
        supportAllCallbacksFromLibrary ? 2 : 1,
        IterableUtils.size(
            myCollection
                .getDexProgramClass()
                .virtualMethods(m -> m.getReference().name.toString().equals("forEach"))));
  }

  private void assertWrapperMethodsPresent(CodeInspector inspector) {
    List<FoundClassSubject> wrappers =
        inspector.allClasses().stream()
            .filter(
                c ->
                    !c.getFinalName()
                        .startsWith(
                            "com.android.tools.r8.desugar.desugaredlibrary.conversiontests"))
            .collect(Collectors.toList());
    for (FoundClassSubject wrapper : wrappers) {
      assertTrue(wrapper.virtualMethods().size() > 0);
    }
  }

  static class CustomLibClass {

    public static void callForEach(Iterable<?> iterable) {
      iterable.forEach(x -> System.out.println("action called from java consumer"));
    }
  }

  static class Executor {

    @SuppressWarnings("RedundantOperationOnEmptyContainer")
    public static void main(String[] args) {
      MyCollection<String> strings = new MyCollection<>();
      // Call foreach with j$ consumer.
      strings.forEach(x -> System.out.println("action called from j$ consumer"));
      // Call foreach with java consumer.
      CustomLibClass.callForEach(strings);
    }
  }

  interface MyCollectionInterface<E> extends Collection<E> {

    // The following method override a method from Iterable and use a desugared type.
    // API conversion is required.
    @Override
    default void forEach(Consumer<? super E> action) {
      action.accept(null);
      System.out.println("forEach called");
    }
  }

  @SuppressWarnings("ConstantConditions")
  static class MyCollection<E> implements MyCollectionInterface<E> {

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
      return Collections.<E>singletonList(null).iterator();
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
