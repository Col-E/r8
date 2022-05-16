// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.SPECIFICATIONS_WITH_CF2CF;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IterableTest extends DesugaredLibraryTestBase {

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines("1", "2", "3", "4", "5", "Count: 4", "1", "2", "3", "4", "5");

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getJdk8Jdk11(),
        SPECIFICATIONS_WITH_CF2CF);
  }

  public IterableTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void testIterable() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private void inspect(CodeInspector inspector) {
    if (compilationSpecification.isProgramShrink()) {
      return;
    }
    if (parameters
        .getApiLevel()
        .isGreaterThanOrEqualTo(apiLevelWithDefaultInterfaceMethodsSupport())) {
      assertThat(
          inspector.clazz(MyIterableSub.class).uniqueMethodWithFinalName("myForEach"),
          CodeMatchers.invokesMethod(null, MyIterable.class.getTypeName(), "forEach", null));
    } else {
      assertThat(
          inspector.clazz(MyIterableSub.class).uniqueMethodWithFinalName("myForEach"),
          CodeMatchers.invokesMethod(null, "j$.lang.Iterable$-CC", "$default$forEach", null));
    }
  }

  static class Main {

    public static void main(String[] args) {
      Iterable<Integer> iterable = new MyIterable<>(Arrays.asList(1, 2, 3, 4, 5));
      iterable.forEach(System.out::println);
      Stream<Integer> stream = StreamSupport.stream(iterable.spliterator(), false);
      System.out.println("Count: " + stream.filter(x -> x != 3).count());
      MyIterableSub<Integer> iterableSub = new MyIterableSub<>(Arrays.asList(1, 2, 3, 4, 5));
      iterableSub.myForEach(System.out::println);
    }
  }

  static class MyIterable<E> implements Iterable<E> {

    private Collection<E> collection;

    public MyIterable(Collection<E> collection) {
      this.collection = collection;
    }

    @Override
    public Iterator<E> iterator() {
      return collection.iterator();
    }
  }

  static class MyIterableSub<E> extends MyIterable<E> {

    public MyIterableSub(Collection<E> collection) {
      super(collection);
    }

    public void myForEach(Consumer<E> consumer) {
      super.forEach(consumer);
    }
  }
}
