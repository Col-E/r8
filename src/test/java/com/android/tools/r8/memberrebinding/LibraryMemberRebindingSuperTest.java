// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.memberrebinding;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.utils.AndroidApiLevel.B;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.apimodel.ApiModelingTestHelper;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// This is a reproduction of b/215573892.
@RunWith(Parameterized.class)
public class LibraryMemberRebindingSuperTest extends TestBase {

  private final String EXPECTED =
      StringUtils.lines("ProgramClass::foo", "LibrarySub::foo", "LibraryBase::foo");
  private final String R8_INVALID = StringUtils.lines("ProgramClass::foo", "LibraryBase::foo");

  private final Class<?>[] LIBRARY_CLASSES =
      new Class<?>[] {LibraryBase.class, LibrarySub.class, LibrarySubSub.class};

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addLibraryClasses(LIBRARY_CLASSES)
        .addDefaultRuntimeLibrary(parameters)
        .addProgramClasses(ProgramClass.class, Main.class)
        .applyIf(
            parameters.isDexRuntime(),
            builder -> builder.addRunClasspathFiles(buildOnDexRuntime(parameters, LIBRARY_CLASSES)))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addLibraryClasses(LIBRARY_CLASSES)
        .addDefaultRuntimeLibrary(parameters)
        .addProgramClasses(ProgramClass.class, Main.class)
        .apply(setMockApiLevelForMethod(LibraryBase.class.getDeclaredMethod("foo"), B))
        .apply(setMockApiLevelForMethod(LibrarySub.class.getDeclaredMethod("foo"), B))
        .apply(setMockApiLevelForClass(LibraryBase.class, B))
        .apply(setMockApiLevelForClass(LibrarySub.class, B))
        .apply(setMockApiLevelForClass(LibrarySubSub.class, B))
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .apply(ApiModelingTestHelper::disableOutliningAndStubbing)
        .addKeepAllClassesRule()
        .setMinApi(parameters)
        .compile()
        .addRunClasspathClasses(LIBRARY_CLASSES)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  public static class LibraryBase {

    public void foo() {
      System.out.println("LibraryBase::foo");
    }
  }

  public static class LibrarySub extends LibraryBase {

    @Override
    public void foo() {
      System.out.println("LibrarySub::foo");
      super.foo();
    }
  }

  public static class LibrarySubSub extends LibrarySub {}

  public static class ProgramClass extends LibrarySubSub {

    @Override
    public void foo() {
      System.out.println("ProgramClass::foo");
      super.foo();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new ProgramClass().foo();
    }
  }
}
