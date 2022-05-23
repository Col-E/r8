// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.verifyThat;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.R8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.R8_L8SHRINK;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelNoDesugaredLibraryReferenceTest extends DesugaredLibraryTestBase {

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

  public ApiModelNoDesugaredLibraryReferenceTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void test() throws Throwable {
    Method printZone = LibUser.class.getDeclaredMethod("printPath");
    Method main = Executor.class.getDeclaredMethod("main", String[].class);
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramClasses(Executor.class, LibUser.class)
        .addKeepMainRule(Executor.class)
        .applyOnBuilder(ApiModelingTestHelper::enableApiCallerIdentification)
        // We are testing that we do not inline/merge higher api-levels
        .applyOnBuilder(ApiModelingTestHelper::disableOutliningAndStubbing)
        .applyOnBuilder(
            b ->
                ApiModelingTestHelper.addTracedApiReferenceLevelCallBack(
                        (reference, apiLevel) -> {
                          if (reference.equals(Reference.methodFromMethod(printZone))) {
                            assertEquals(AndroidApiLevel.O.max(parameters.getApiLevel()), apiLevel);
                          }
                        })
                    .acceptWithRuntimeException(b))
        .run(parameters.getRuntime(), Executor.class)
        .applyIf(
            parameters.getDexRuntimeVersion().isDalvik(),
            result -> result.assertSuccessWithOutputLines("java.nio.file.Paths"))
        .applyIf(
            parameters.getDexRuntimeVersion().isInRangeInclusive(Version.V5_1_1, Version.V7_0_0),
            result ->
                result.assertSuccessWithOutputLines("Failed resolution of: Ljava/nio/file/Paths;"))
        .applyIf(
            parameters.getDexRuntimeVersion().isNewerThan(Version.V7_0_0),
            result -> result.assertSuccessWithOutputLines("~"))
        .inspect(
            inspector -> {
              verifyThat(inspector, parameters, printZone)
                  .inlinedIntoFromApiLevel(main, AndroidApiLevel.O);
            });
  }

  static class Executor {

    public static void main(String[] args) {
      try {
        LibUser.printPath();
      } catch (Throwable t) {
        System.out.println(t.getMessage());
      }
    }
  }

  static class LibUser {

    public static void printPath() {
      // java.nio.Path is not (yet) library desugared.
      System.out.println(Paths.get("~"));
    }
  }
}
