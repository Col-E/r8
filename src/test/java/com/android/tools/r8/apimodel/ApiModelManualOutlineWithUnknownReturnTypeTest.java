// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/***
 * This is a regression test for b/272725341 where we manually outline to show runtime behavior.
 */
@RunWith(Parameterized.class)
public class ApiModelManualOutlineWithUnknownReturnTypeTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameter(1)
  public boolean addedToLibraryHere;

  @Parameters(name = "{0}, addedToLibraryHere: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  private AndroidApiLevel runApiLevel() {
    return parameters.getRuntime().maxSupportedApiLevel();
  }

  private AndroidApiLevel getMockApiLevel() {
    return addedToLibraryHere ? runApiLevel() : runApiLevel().next();
  }

  @Test
  public void testJvm() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForJvm(parameters)
        .addProgramClasses(Main.class, ProgramClass.class, ManualOutline.class)
        .addDefaultRuntimeLibrary(parameters)
        .addLibraryClasses(LibraryClass.class)
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(NoClassDefFoundError.class)
        .assertFailureWithErrorThatMatches(containsString(typeName(LibrarySub.class)));
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
  public void testD8WithModeling() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8(parameters.getBackend())
        .addProgramClasses(Main.class, ProgramClass.class, ManualOutline.class)
        .addDefaultRuntimeLibrary(parameters)
        .addLibraryClasses(LibraryClass.class, LibrarySub.class)
        .setMinApi(parameters)
        .addOptionsModification(
            options -> options.apiModelingOptions().disableOutliningAndStubbing())
        .apply(setMockApiLevelForClass(LibraryClass.class, AndroidApiLevel.B))
        .apply(setMockApiLevelForClass(LibrarySub.class, getMockApiLevel()))
        .compile()
        .apply(this::setupRuntime)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("ProgramClass::print");
  }

  @Test
  public void testD8NoModeling() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    boolean willHaveVerifyError =
        (parameters.getDexRuntimeVersion().isDalvik()
                || parameters.isDexRuntimeVersion(Version.V12_0_0))
            && !addedToLibraryHere;
    testForD8(parameters.getBackend())
        .addProgramClasses(Main.class, ProgramClass.class, ManualOutline.class)
        .addDefaultRuntimeLibrary(parameters)
        .addLibraryClasses(LibraryClass.class, LibrarySub.class)
        .setMinApi(parameters)
        .addOptionsModification(options -> options.apiModelingOptions().disableApiModeling())
        .compile()
        .apply(this::setupRuntime)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLinesIf(!willHaveVerifyError, "ProgramClass::print")
        .assertFailureWithErrorThatThrowsIf(willHaveVerifyError, VerifyError.class);
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

  public static class ManualOutline {

    public static LibrarySub create() {
      return LibrarySub.create();
    }
  }

  public static class ProgramClass {

    public LibraryClass callLibraryWithDirectReturn() {
      LibrarySub libraryClass = ManualOutline.create();
      if (System.currentTimeMillis() > 0) {
        return null;
      } else {
        return libraryClass;
      }
    }

    public LibraryClass callLibraryWithIndirectReturn() {
      LibrarySub libraryClass = ManualOutline.create();
      LibraryClass libClass;
      if (System.currentTimeMillis() > 0) {
        libClass = null;
      } else {
        libClass = libraryClass;
      }
      return libClass;
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
