// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.R8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.R8_L8SHRINK;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.references.Reference;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.Method;
import java.time.Clock;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelDesugaredLibraryReferenceTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getJdk8Jdk11(),
        ImmutableList.of(R8_L8DEBUG, R8_L8SHRINK));
  }

  public ApiModelDesugaredLibraryReferenceTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void testClock() throws Throwable {
    Method printZone = DesugaredLibUser.class.getDeclaredMethod("printZone");
    Method main = Executor.class.getDeclaredMethod("main", String[].class);
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramClasses(Executor.class, DesugaredLibUser.class)
        .addKeepMainRule(Executor.class)
        .applyOnBuilder(ApiModelingTestHelper::enableApiCallerIdentification)
        .applyOnBuilder(
            b ->
                ApiModelingTestHelper.addTracedApiReferenceLevelCallBack(
                        (reference, apiLevel) -> {
                          if (reference.equals(Reference.methodFromMethod(printZone))) {
                            assertEquals(parameters.getApiLevel(), apiLevel);
                          }
                        })
                    .acceptWithRuntimeException(b))
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutputLines("Z")
        .inspect(
            inspector ->
                ApiModelingTestHelper.verifyThat(inspector, parameters, printZone)
                    .inlinedInto(main));
  }

  static class Executor {

    public static void main(String[] args) {
      DesugaredLibUser.printZone();
    }
  }

  static class DesugaredLibUser {

    public static void printZone() {
      System.out.println(Clock.systemUTC().getZone());
    }
  }
}
