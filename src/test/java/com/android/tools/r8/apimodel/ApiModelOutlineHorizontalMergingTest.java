// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForDefaultInstanceInitializer;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithName;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelOutlineHorizontalMergingTest extends TestBase {

  private final AndroidApiLevel libraryClassApiLevel = AndroidApiLevel.K;
  private final AndroidApiLevel firstMethodApiLevel = AndroidApiLevel.M;
  private final AndroidApiLevel secondMethodApiLevel = AndroidApiLevel.O_MR1;

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private void setupTestBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) throws Exception {
    testBuilder
        .addProgramClasses(Main.class, TestClass.class)
        .addLibraryClasses(LibraryClass.class, OtherLibraryClass.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .addAndroidBuildVersion()
        .apply(setMockApiLevelForClass(LibraryClass.class, libraryClassApiLevel))
        .apply(
            setMockApiLevelForDefaultInstanceInitializer(LibraryClass.class, libraryClassApiLevel))
        .apply(
            setMockApiLevelForMethod(
                LibraryClass.class.getMethod("addedOn23"), firstMethodApiLevel))
        .apply(
            setMockApiLevelForMethod(
                LibraryClass.class.getMethod("addedOn27"), secondMethodApiLevel))
        .apply(setMockApiLevelForClass(OtherLibraryClass.class, libraryClassApiLevel))
        .apply(
            setMockApiLevelForDefaultInstanceInitializer(
                OtherLibraryClass.class, libraryClassApiLevel))
        .apply(
            setMockApiLevelForMethod(
                OtherLibraryClass.class.getMethod("addedOn23"), firstMethodApiLevel))
        .apply(
            setMockApiLevelForMethod(
                OtherLibraryClass.class.getMethod("addedOn27"), secondMethodApiLevel))
        // TODO(b/213552119): Remove when enabled by default.
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .apply(ApiModelingTestHelper::enableOutliningOfMethods)
        .apply(ApiModelingTestHelper::disableStubbingOfClasses);
  }

  public boolean addToBootClasspath() {
    return parameters.isDexRuntime()
        && parameters
            .getRuntime()
            .maxSupportedApiLevel()
            .isGreaterThanOrEqualTo(libraryClassApiLevel);
  }

  @Test
  public void testD8Debug() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8(parameters.getBackend())
        .setMode(CompilationMode.DEBUG)
        .apply(this::setupTestBuilder)
        .compile()
        .applyIf(
            addToBootClasspath(),
            b -> b.addBootClasspathClasses(LibraryClass.class, OtherLibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput)
        .inspect(
            inspector -> {
              // TODO(b/187675788): Update when horizontal merging is enabled for D8 for debug mode.
              if (parameters.getApiLevel().isLessThan(AndroidApiLevel.L)) {
                assertEquals(7, inspector.allClasses().size());
              } else if (parameters.getApiLevel().isLessThan(libraryClassApiLevel)) {
                // We have generated 4 outlines two having api level 23 and two having api level 27
                // and 2 outlines for each instance initializer.
                assertEquals(11, inspector.allClasses().size());
              } else if (parameters.getApiLevel().isLessThan(firstMethodApiLevel)) {
                // We have generated 4 outlines two having api level 23 and two having api level 27.
                assertEquals(7, inspector.allClasses().size());
              } else if (parameters.getApiLevel().isLessThan(secondMethodApiLevel)) {
                assertEquals(5, inspector.allClasses().size());
              } else {
                // No outlining on this api level.
                assertEquals(3, inspector.allClasses().size());
              }
            });
  }

  @Test
  public void testD8Release() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8(parameters.getBackend())
        .setMode(CompilationMode.RELEASE)
        .apply(this::setupTestBuilder)
        .compile()
        .applyIf(
            addToBootClasspath(),
            b -> b.addBootClasspathClasses(LibraryClass.class, OtherLibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput)
        .inspect(this::inspect);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .apply(this::setupTestBuilder)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .compile()
        .applyIf(
            addToBootClasspath(),
            b -> b.addBootClasspathClasses(LibraryClass.class, OtherLibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput)
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    List<FoundMethodSubject> outlinedAddedOn23 =
        inspector.allClasses().stream()
            .flatMap(clazz -> clazz.allMethods().stream())
            .filter(
                methodSubject ->
                    methodSubject.isSynthetic()
                        && invokesMethodWithName("addedOn23").matches(methodSubject))
            .collect(Collectors.toList());
    List<FoundMethodSubject> outlinedAddedOn27 =
        inspector.allClasses().stream()
            .flatMap(clazz -> clazz.allMethods().stream())
            .filter(
                methodSubject ->
                    methodSubject.isSynthetic()
                        && invokesMethodWithName("addedOn27").matches(methodSubject))
            .collect(Collectors.toList());
    if (parameters.isCfRuntime()) {
      assertTrue(outlinedAddedOn23.isEmpty());
      assertTrue(outlinedAddedOn27.isEmpty());
      assertEquals(3, inspector.allClasses().size());
    } else if (parameters.getApiLevel().isLessThan(firstMethodApiLevel)) {
      // We have generated 4 outlines two having api level 23 and two having api level 27.
      // If less than the library api level then we have synthesized two instance initializer
      // outlines as well.
      // Check that the levels are horizontally merged.
      boolean willOutlineInitializers =
          parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.L)
              && parameters.getApiLevel().isLessThan(libraryClassApiLevel);
      assertEquals(willOutlineInitializers ? 6 : 5, inspector.allClasses().size());
      assertEquals(2, outlinedAddedOn23.size());
      assertTrue(
          outlinedAddedOn23.stream()
              .allMatch(
                  outline ->
                      outline.getMethod().getHolderType()
                          == outlinedAddedOn23.get(0).getMethod().getHolderType()));
      assertEquals(2, outlinedAddedOn27.size());
      assertTrue(
          outlinedAddedOn27.stream()
              .allMatch(
                  outline ->
                      outline.getMethod().getHolderType()
                          == outlinedAddedOn27.get(0).getMethod().getHolderType()));
    } else if (parameters.getApiLevel().isLessThan(secondMethodApiLevel)) {
      assertTrue(outlinedAddedOn23.isEmpty());
      assertEquals(4, inspector.allClasses().size());
      assertEquals(2, outlinedAddedOn27.size());
      assertTrue(
          outlinedAddedOn27.stream()
              .allMatch(
                  outline ->
                      outline.getMethod().getHolderType()
                          == outlinedAddedOn27.get(0).getMethod().getHolderType()));
    } else {
      // No outlining on this api level.
      assertTrue(outlinedAddedOn23.isEmpty());
      assertTrue(outlinedAddedOn27.isEmpty());
      assertEquals(3, inspector.allClasses().size());
    }
  }

  private void checkOutput(SingleTestRunResult<?> runResult) {
    boolean beforeFirstApiMethodLevel =
        parameters.isCfRuntime() || parameters.getApiLevel().isLessThan(firstMethodApiLevel);
    boolean afterSecondApiMethodLevel =
        parameters.isDexRuntime()
            && parameters.getApiLevel().isGreaterThanOrEqualTo(secondMethodApiLevel);
    if (beforeFirstApiMethodLevel) {
      runResult.assertSuccessWithOutputLines("Hello World");
    } else if (afterSecondApiMethodLevel) {
      runResult.assertSuccessWithOutputLines(
          "LibraryClass::addedOn23",
          "LibraryClass::addedOn27",
          "OtherLibraryClass::addedOn23",
          "OtherLibraryClass::addedOn27",
          "Hello World");
    } else {
      runResult.assertSuccessWithOutputLines(
          "LibraryClass::addedOn23", "OtherLibraryClass::addedOn23", "Hello World");
    }
  }

  // Only present from api level 19.
  public static class LibraryClass {

    public void addedOn23() {
      System.out.println("LibraryClass::addedOn23");
    }

    public void addedOn27() {
      System.out.println("LibraryClass::addedOn27");
    }
  }

  // Only present from api level 19.
  public static class OtherLibraryClass {

    public void addedOn23() {
      System.out.println("OtherLibraryClass::addedOn23");
    }

    public static void addedOn27() {
      System.out.println("OtherLibraryClass::addedOn27");
    }
  }

  public static class TestClass {

    @NeverInline
    public static void test() {
      if (AndroidBuildVersion.VERSION >= 19) {
        LibraryClass libraryClass = new LibraryClass();
        if (AndroidBuildVersion.VERSION >= 23) {
          libraryClass.addedOn23();
        }
        if (AndroidBuildVersion.VERSION >= 27) {
          libraryClass.addedOn27();
        }
      }
      if (AndroidBuildVersion.VERSION >= 19) {
        OtherLibraryClass otherLibraryClass = new OtherLibraryClass();
        if (AndroidBuildVersion.VERSION >= 23) {
          otherLibraryClass.addedOn23();
        }
        if (AndroidBuildVersion.VERSION >= 27) {
          OtherLibraryClass.addedOn27();
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
