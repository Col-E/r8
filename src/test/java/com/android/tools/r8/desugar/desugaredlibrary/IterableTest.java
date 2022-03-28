// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.LibraryDesugaringTestConfiguration;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IterableTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;
  private static final String EXPECTED_OUTPUT =
      StringUtils.lines("1", "2", "3", "4", "5", "Count: 4", "1", "2", "3", "4", "5");

  @Parameters(name = "{1}, shrinkDesugaredLibrary: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build());
  }

  public IterableTest(boolean shrinkDesugaredLibrary, TestParameters parameters) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  private void inspect(CodeInspector inspector) {
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

  @Test
  public void testIterableD8Cf() throws Exception {
    // Only test without shrinking desugared library.
    Assume.assumeFalse(shrinkDesugaredLibrary);
    // Use D8 to desugar with Java classfile output.
    Path jar =
        testForD8(Backend.CF)
            .addInnerClasses(IterableTest.class)
            .setMinApi(parameters.getApiLevel())
            .enableCoreLibraryDesugaring(
                LibraryDesugaringTestConfiguration.forApiLevel(parameters.getApiLevel()))
            .compile()
            .inspect(this::inspect)
            .writeToZip();

    if (parameters.getRuntime().isDex()) {
      // Convert to DEX without desugaring and run.
      testForD8()
          .addProgramFiles(jar)
          .setMinApi(parameters.getApiLevel())
          .disableDesugaring()
          .compile()
          .addRunClasspathFiles(buildDesugaredLibrary(parameters.getApiLevel()))
          .run(parameters.getRuntime(), Main.class)
          .assertSuccessWithOutput(EXPECTED_OUTPUT);
    } else {
      // Run on the JVM with desugared library on classpath.
      testForJvm()
          .addProgramFiles(jar)
          .addRunClasspathFiles(buildDesugaredLibraryClassFile(parameters.getApiLevel()))
          .run(parameters.getRuntime(), Main.class)
          .applyIf(
              // TODO(b/227161271): Figure out the cause and resolution for this issue.
              parameters.isCfRuntime(CfVm.JDK17)
                  && parameters.getApiLevel().equals(AndroidApiLevel.B),
              r -> r.assertFailureWithErrorThatThrows(ExceptionInInitializerError.class),
              r -> r.assertSuccessWithOutput(EXPECTED_OUTPUT));
    }
  }

  @Test
  public void testIterable() throws Exception {
    if (parameters.isCfRuntime()) {
      testForJvm()
          .addInnerClasses(IterableTest.class)
          .run(parameters.getRuntime(), Main.class)
          .assertSuccessWithOutput(EXPECTED_OUTPUT);
      return;
    }
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8()
        .addLibraryFiles(getLibraryFile())
        .addInnerClasses(IterableTest.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
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
