// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForDefaultInstanceInitializer;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithName;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.lang.reflect.Method;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelOutlineDuplicateMethodTest extends TestBase {

  private final AndroidApiLevel classApiLevel = AndroidApiLevel.K;
  private final AndroidApiLevel methodApiLevel = AndroidApiLevel.M;

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    assumeFalse(
        parameters.isDexRuntime() && parameters.getDexRuntimeVersion().isEqualTo(Version.V12_0_0));
    boolean isMethodApiLevel =
        parameters.isDexRuntime()
            && parameters.getApiLevel().isGreaterThanOrEqualTo(methodApiLevel);
    Method adeddOn23 = LibraryClass.class.getMethod("addedOn23");
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, TestClass.class)
        .addLibraryClasses(LibraryClass.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .addAndroidBuildVersion()
        .apply(setMockApiLevelForClass(LibraryClass.class, classApiLevel))
        .apply(setMockApiLevelForDefaultInstanceInitializer(LibraryClass.class, classApiLevel))
        .apply(setMockApiLevelForMethod(adeddOn23, methodApiLevel))
        .apply(ApiModelingTestHelper::enableOutliningOfMethods)
        .enableInliningAnnotations()
        .compile()
        .applyIf(
            parameters.isDexRuntime()
                && parameters
                    .getRuntime()
                    .maxSupportedApiLevel()
                    .isGreaterThanOrEqualTo(classApiLevel),
            b -> b.addBootClasspathClasses(LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLinesIf(!isMethodApiLevel, "Hello World")
        .assertSuccessWithOutputLinesIf(
            isMethodApiLevel, "LibraryClass::addedOn23", "LibraryClass::addedOn23", "Hello World")
        .inspect(
            inspector -> {
              // No need to check further on CF.
              Optional<FoundMethodSubject> synthesizedAddedOn23 =
                  inspector.allClasses().stream()
                      .flatMap(clazz -> clazz.allMethods().stream())
                      .filter(
                          methodSubject ->
                              methodSubject.isSynthetic()
                                  && invokesMethodWithName("addedOn23").matches(methodSubject))
                      .findFirst();
              if (parameters.isCfRuntime() || parameters.getApiLevel().isLessThan(classApiLevel)) {
                assertFalse(synthesizedAddedOn23.isPresent());
                assertEquals(3, inspector.allClasses().size());
              } else if (parameters.getApiLevel().isLessThan(methodApiLevel)) {
                assertTrue(synthesizedAddedOn23.isPresent());
                assertEquals(4, inspector.allClasses().size());
                ClassSubject testClass = inspector.clazz(TestClass.class);
                assertThat(testClass, isPresent());
                MethodSubject testMethod = testClass.uniqueMethodWithName("test");
                assertThat(testMethod, isPresent());
                assertEquals(
                    2,
                    testMethod
                        .streamInstructions()
                        .filter(
                            instructionSubject -> {
                              if (!instructionSubject.isInvoke()) {
                                return false;
                              }
                              return instructionSubject
                                  .getMethod()
                                  .asMethodReference()
                                  .equals(synthesizedAddedOn23.get().asMethodReference());
                            })
                        .count());
              } else {
                // No outlining on this api level.
                assertFalse(synthesizedAddedOn23.isPresent());
                assertEquals(3, inspector.allClasses().size());
              }
            });
  }

  // Only present from api level 19.
  public static class LibraryClass {

    public void addedOn23() {
      System.out.println("LibraryClass::addedOn23");
    }
  }

  public static class TestClass {

    @NeverInline
    public static void test() {
      if (AndroidBuildVersion.VERSION >= 19) {
        LibraryClass libraryClass = new LibraryClass();
        if (AndroidBuildVersion.VERSION >= 23) {
          libraryClass.addedOn23();
          libraryClass.addedOn23();
        }
      }
      System.out.println("Hello World");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      TestClass.test();
    }
  }
}
