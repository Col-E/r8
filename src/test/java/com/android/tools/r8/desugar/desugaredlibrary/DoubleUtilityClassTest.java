// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.zone.ZoneOffsetTransition;
import java.util.List;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// In this test both the desugared library and the program have the same utility class.
@RunWith(Parameterized.class)
public class DoubleUtilityClassTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final CompilationSpecification compilationSpecification;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getJdk8Jdk11(),
        DEFAULT_SPECIFICATIONS);
  }

  public DoubleUtilityClassTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  @Test
  public void testDoubleUtility() throws Exception {
    for (Class<?> executor : new Class<?>[] {ExecutorV1.class, ExecutorV2.class}) {
      testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
          .addProgramClasses(executor)
          .addKeepMainRule(executor)
          .run(parameters.getRuntime(), executor)
          .assertSuccess()
          // Verification error on some Dalvik VMs (4,api 1;4,api 15;4.4,api 1).
          .assertStderrMatches(
              not(
                  containsString(
                      "(Lcom/android/tools/r8/desugar/desugaredlibrary/DoubleUtilityClassTest$Executor;"
                          + " had used a different"
                          + " L$r8$backportedMethods$utility$Objects$requireNonNullMessage; during"
                          + " pre-verification)  (dalvikvm)")));
    }
  }

  // ExecutorV1 and V2 look the same, *but* only one of them triggers the verification
  // error on Dalvik VMs.
  public static class ExecutorV1 {

    public static void main(String[] args) {
      programCalls();
    }

    public static void programCalls() {
      System.out.println(Objects.requireNonNull("string", "transition"));
      System.out.println(Objects.hashCode("foo"));
      System.out.println(Long.hashCode(1L));
      System.out.println(Long.compareUnsigned(1L, 2L));
      System.out.println(Integer.hashCode(1));
      System.out.println(Double.hashCode(1.0));
    }
  }

  public static class ExecutorV2 {

    public static void main(String[] args) {
      programCalls();
      libraryCalls();
    }

    public static void libraryCalls() {
      System.out.println(
          ZoneOffsetTransition.of(
              LocalDateTime.of(2008, 3, 9, 2, 0, 0, 0), ZoneOffset.UTC, ZoneOffset.MAX));
    }

    public static void programCalls() {
      System.out.println(Objects.requireNonNull("string", "transition"));
      System.out.println(Objects.hashCode("foo"));
      System.out.println(Long.hashCode(1L));
      System.out.println(Long.compareUnsigned(1L, 2L));
      System.out.println(Integer.hashCode(1));
      System.out.println(Double.hashCode(1.0));
    }
  }
}
