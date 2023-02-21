// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForDefaultInstanceInitializer;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelInlineMissingInterfaceTest extends TestBase {

  private final AndroidApiLevel libraryAdditionApiLevel = AndroidApiLevel.M;

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    boolean isApiLevel =
        parameters.isDexRuntime()
            && parameters.getApiLevel().isGreaterThanOrEqualTo(libraryAdditionApiLevel);
    testForR8(parameters.getBackend())
        .addProgramClasses(ProgramClass.class, VerificationError.class, Main.class)
        .addLibraryClasses(LibraryClass.class)
        .addDefaultRuntimeLibrary(parameters)
        .addAndroidBuildVersion()
        .apply(setMockApiLevelForClass(LibraryClass.class, libraryAdditionApiLevel))
        .apply(
            setMockApiLevelForDefaultInstanceInitializer(
                LibraryClass.class, libraryAdditionApiLevel))
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .compile()
        .inspect(
            inspector -> {
              // Dex2Oat do not complain about missing super interfaces since these are not verified
              // the same way as super types. We therefore expect the class to be absent.
              assertThat(inspector.clazz(VerificationError.class), Matchers.isAbsent());
            })
        .applyIf(isApiLevel, b -> b.addRunClasspathClasses(LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLinesIf(isApiLevel, "Hello World")
        .assertSuccessWithOutputLinesIf(!isApiLevel, "Lower Api Level");
  }

  public interface LibraryClass {}

  public static class ProgramClass implements LibraryClass {

    @NeverInline
    public void foo() {
      System.out.println("Hello World");
    }
  }

  public static class VerificationError {

    public static ProgramClass create() {
      return new ProgramClass();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      if (AndroidBuildVersion.VERSION >= 23) {
        VerificationError.create().foo();
      } else {
        System.out.println("Lower Api Level");
      }
    }
  }
}
