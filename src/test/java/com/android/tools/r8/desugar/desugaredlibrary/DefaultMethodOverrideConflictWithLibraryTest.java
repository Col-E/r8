// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * This test checks that if a default interface method in a library is not overridden by a class
 * method in the library, then multiple maximally specific method lead to ICCE.
 *
 * <p>Concretely, Collection defines a default removeIf() method which is not overridden in either
 * the List interface or the LinkedList class. Thus, a class that has an unrelated default method
 * for removeIf will cause a conflict throwing ICCE.
 */
@RunWith(Parameterized.class)
public class DefaultMethodOverrideConflictWithLibraryTest extends DesugaredLibraryTestBase {

  private static List<Class<?>> CLASSES = ImmutableList.of(Main.class, MyRemoveIf.class);

  private static List<byte[]> getTransforms() throws IOException {
    return ImmutableList.of(
        transformer(ConflictingClass.class).setImplements(MyRemoveIf.class).transform());
  }

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

  public DefaultMethodOverrideConflictWithLibraryTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  @Test
  public void test() throws Exception {
    TestRuntime systemRuntime = TestRuntime.getSystemRuntime();
    if (systemRuntime.isCf() && systemRuntime.asCf().isNewerThanOrEqual(CfVm.JDK8)) {
      // This test assumes that the library defines a Collection class with a default removeIf.
      Method removeIf = Collection.class.getDeclaredMethod("removeIf", Predicate.class);
      assertNotNull(removeIf);
      assertTrue(removeIf.isDefault());
      // Also, the LinkedList implementation does *not* define an override of removeIf.
      try {
        LinkedList.class.getDeclaredMethod("removeIf", Predicate.class);
        fail("Unexpected defintion of removeIf on LinkedList");
      } catch (NoSuchMethodException e) {
        // Expected.
      }
    }
    if (parameters.isCfRuntime()) {
      testForJvm(parameters)
          .addProgramClasses(CLASSES)
          .addProgramClassFileData(getTransforms())
          .run(parameters.getRuntime(), Main.class)
          .assertFailureWithErrorThatMatches(getExpectedError());
    } else {
      testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
          .addProgramClasses(CLASSES)
          .addProgramClassFileData(getTransforms())
          .addKeepMainRule(Main.class)
          .run(parameters.getRuntime(), Main.class)
          .assertFailureWithErrorThatMatches(getExpectedError());
    }
  }

  private Matcher<String> getExpectedError() {
    return containsString(IncompatibleClassChangeError.class.getName());
  }

  // Interface with a default method that can lead to a conflict.
  interface MyRemoveIf {

    default boolean removeIf(Predicate<? super Integer> filter) {
      System.out.println("MyRemoveIf::removeIf");
      return false;
    }
  }

  // Derived list with no override of removeIf but with a default method in MyRemoveIf.
  // The two maximally specific methods Collection::removeIf and MyRemoveIf must cause ICCE.
  static class ConflictingClass extends LinkedList<Integer> /* implements MyRemoveIf via ASM */ {
    // Intentionally empty.
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(new ConflictingClass().removeIf(e -> false));
    }
  }
}
