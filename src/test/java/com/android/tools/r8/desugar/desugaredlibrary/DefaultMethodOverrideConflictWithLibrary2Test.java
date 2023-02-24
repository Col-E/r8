// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * This test checks that if a default interface method in a library interface conflicts with a
 * default method in a program interface, then deriving both leads to a ICCE.
 *
 * <p>In contrast to DefaultMethodOverrideConflictWithLibraryTest, in this test, the conflict is in
 * a class with the two conflicting interfaces as the immediate interfaces.
 */
@RunWith(Parameterized.class)
public class DefaultMethodOverrideConflictWithLibrary2Test extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final CompilationSpecification compilationSpecification;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevels().build(),
        getJdk8Jdk11(),
        ImmutableList.of(D8_L8DEBUG));
  }

  public DefaultMethodOverrideConflictWithLibrary2Test(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  private List<Class<?>> getClasses() {
    return ImmutableList.of(
        Main.class,
        MyIntegerList.class,
        MyIntegerArrayListWithOverride.class,
        MyHasCharacteristics.class);
  }

  private List<byte[]> getTransforms() throws IOException {
    return ImmutableList.of(
        transformer(MySpliterator.class)
            .setImplements(Spliterator.class, MyHasCharacteristics.class)
            .transform());
  }

  @Test
  public void test() throws Exception {
    TestRuntime systemRuntime = TestRuntime.getSystemRuntime();
    if (systemRuntime.isCf() && systemRuntime.asCf().isNewerThanOrEqual(CfVm.JDK8)) {
      // This test assumes that the library defines default method Spliterator::hasCharacteristics.
      Method method = Spliterator.class.getDeclaredMethod("hasCharacteristics", int.class);
      assertNotNull(method);
      assertTrue(method.isDefault());
    }
    if (parameters.isCfRuntime()) {
      testForJvm(parameters)
          .addProgramClasses(getClasses())
          .addProgramClassFileData(getTransforms())
          .run(parameters.getRuntime(), Main.class)
          .apply(this::checkResult);
    } else {
      testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
          .addProgramClasses(getClasses())
          .addProgramClassFileData(getTransforms())
          .addKeepMainRule(Main.class)
          .run(parameters.getRuntime(), Main.class)
          .apply(this::checkResult);
    }
  }

  private void checkResult(TestRunResult<?> result) {
    if (parameters.isCfRuntime() && parameters.getRuntime().asCf().isNewerThanOrEqual(CfVm.JDK11)) {
      // TODO(b/145566657): For some reason JDK11+ throws AbstractMethodError.
      result.assertFailureWithErrorThatThrows(AbstractMethodError.class);
    } else {
      result.assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class);
    }
  }

  // Interface that will lead to a conflicting default method with Spliterator::hasCharacteristics.
  interface MyHasCharacteristics {

    default boolean hasCharacteristics(int characteristics) {
      System.out.println("MyHasCharacteristics::hasCharacteristics");
      return false;
    }
  }

  // Class deriving the default methods. The resolution of which will throw ICCE.
  static class MySpliterator implements Spliterator<Integer> /*, MyHasCharacteristics via ASM */ {

    @Override
    public boolean tryAdvance(Consumer<? super Integer> action) {
      return false;
    }

    @Override
    public Spliterator<Integer> trySplit() {
      return null;
    }

    @Override
    public long estimateSize() {
      return 0;
    }

    @Override
    public int characteristics() {
      return 0;
    }
  }

  // Custom list interface with a default method for spliterator.
  interface MyIntegerList extends List<Integer> {

    @Override
    default Spliterator<Integer> spliterator() {
      return new MySpliterator();
    }
  }

  // Derived list with override of spliterator that will explicitly call the custom default method.
  static class MyIntegerArrayListWithOverride extends ArrayList<Integer> implements MyIntegerList {

    @Override
    public Spliterator<Integer> spliterator() {
      return MyIntegerList.super.spliterator();
    }
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(new MyIntegerArrayListWithOverride().spliterator().hasCharacteristics(0));
    }
  }
}
