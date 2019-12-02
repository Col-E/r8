// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.zone.ZoneOffsetTransition;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

// In this test both the desugared library and the program have the same utility class.
@RunWith(Parameterized.class)
public class DoubleUtilityClassTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public DoubleUtilityClassTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testDoubleUtility() throws Exception {
    for (Class<?> executor : new Class<?>[] {ExecutorV1.class, ExecutorV2.class}) {
      testForD8()
          .addProgramClasses(executor)
          .enableCoreLibraryDesugaring(parameters.getApiLevel())
          .setMinApi(parameters.getApiLevel())
          .compile()
          .addDesugaredCoreLibraryRunClassPath(
              this::buildDesugaredLibrary, parameters.getApiLevel())
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
