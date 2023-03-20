// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.verifyThat;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/***
 * This is a regression test for b/272725341.
 */
@RunWith(Parameterized.class)
public class ApiModelOutlineWithUnknownReturnTypeTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameter(1)
  public boolean addedToLibraryHere;

  @Parameters(name = "{0}, addedToLibraryHere: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(), BooleanUtils.values());
  }

  private AndroidApiLevel runApiLevel() {
    return parameters.getRuntime().maxSupportedApiLevel();
  }

  private AndroidApiLevel getMockApiLevel() {
    return addedToLibraryHere ? runApiLevel() : runApiLevel().next();
  }

  private void setupTestBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) throws Exception {
    testBuilder
        .addProgramClasses(Main.class, ProgramClass.class)
        .addLibraryClasses(LibraryClass.class, LibrarySub.class)
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.LATEST))
        .setMinApi(parameters)
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .apply(ApiModelingTestHelper::enableOutliningOfMethods)
        .apply(setMockApiLevelForClass(LibraryClass.class, AndroidApiLevel.B))
        .apply(setMockApiLevelForClass(LibrarySub.class, getMockApiLevel()))
        .apply(
            setMockApiLevelForMethod(
                LibrarySub.class.getDeclaredMethod("create"), getMockApiLevel()));
  }

  private void setupRuntime(TestCompileResult<?, ?> compileResult) throws Exception {
    if (runApiLevel().isGreaterThanOrEqualTo(getMockApiLevel())) {
      compileResult.addBootClasspathFiles(
          buildOnDexRuntime(parameters, LibraryClass.class, LibrarySub.class));
    } else {
      compileResult.addBootClasspathFiles(buildOnDexRuntime(parameters, LibraryClass.class));
    }
  }

  @Test
  public void testD8Debug() throws Exception {
    testForD8()
        .setMode(CompilationMode.DEBUG)
        .apply(this::setupTestBuilder)
        .compile()
        .inspect(this::inspect)
        .apply(this::setupRuntime)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testD8Release() throws Exception {
    testForD8()
        .setMode(CompilationMode.RELEASE)
        .apply(this::setupTestBuilder)
        .compile()
        .inspect(this::inspect)
        .apply(this::setupRuntime)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .apply(this::setupTestBuilder)
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(ProgramClass.class)
        .compile()
        .inspect(this::inspect)
        .apply(this::setupRuntime)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  private void inspect(CodeInspector inspector) throws Exception {
    assertThat(inspector.clazz(ProgramClass.class), isPresent());
    verifyThat(inspector, parameters, LibrarySub.class.getDeclaredMethod("create"))
        .isOutlinedFromBetween(
            ProgramClass.class.getDeclaredMethod("callLibrary"),
            AndroidApiLevel.B,
            getMockApiLevel());
  }

  private void checkOutput(SingleTestRunResult<?> runResult) {
    runResult.assertSuccessWithOutputLines("ProgramClass::print");
  }

  public static class LibraryClass {

    public void foo() {
      System.out.println("LibraryClass::foo");
    }
  }

  public static class LibrarySub extends LibraryClass {

    public static LibrarySub create() {
      return new LibrarySub();
    }
  }

  public static class ProgramClass {

    public LibraryClass callLibrary() {
      LibrarySub libraryClass = LibrarySub.create();
      if (System.currentTimeMillis() > 0) {
        return null;
      } else {
        return libraryClass;
      }
    }

    public void print() {
      System.out.println("ProgramClass::print");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new ProgramClass().print();
    }
  }
}
