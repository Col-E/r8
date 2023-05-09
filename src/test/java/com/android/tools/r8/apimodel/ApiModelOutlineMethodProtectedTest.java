// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForDefaultInstanceInitializer;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// This is a reproduction of b/216136762.
@RunWith(Parameterized.class)
public class ApiModelOutlineMethodProtectedTest extends TestBase {

  private static final AndroidApiLevel classApiLevel = AndroidApiLevel.M;
  private static final AndroidApiLevel methodApiLevel = AndroidApiLevel.O_MR1;

  private static final String TESTCLASS_DESCRIPTOR = "Lfoo/bar/Baz;";

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  private AndroidApiLevel runApiLevel() {
    return parameters.isCfRuntime()
        ? AndroidApiLevel.B
        : parameters.getRuntime().maxSupportedApiLevel();
  }

  private Method[] apiMethods() throws Exception {
    return new Method[] {
      LibraryClass.class.getDeclaredMethod("addedOn27"),
      LibraryClass.class.getDeclaredMethod("alsoAddedOn27"),
      LibraryClass.class.getDeclaredMethod("superInvokeOn27")
    };
  }

  private void setupTestBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) throws Exception {
    testBuilder
        .addLibraryClasses(LibraryClass.class)
        .addLibraryFiles(
            parameters.isCfRuntime()
                ? ToolHelper.getJava8RuntimeJar()
                : ToolHelper.getFirstSupportedAndroidJar(runApiLevel()))
        .addProgramClassFileData(
            transformer(TestClass.class).setClassDescriptor(TESTCLASS_DESCRIPTOR).transform(),
            transformer(Main.class)
                .replaceClassDescriptorInMethodInstructions(
                    descriptor(TestClass.class), TESTCLASS_DESCRIPTOR)
                .transform())
        .setMinApi(AndroidApiLevel.B)
        .addAndroidBuildVersion(runApiLevel())
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .apply(ApiModelingTestHelper::disableStubbingOfClasses)
        .apply(setMockApiLevelForClass(LibraryClass.class, classApiLevel))
        .apply(setMockApiLevelForDefaultInstanceInitializer(LibraryClass.class, classApiLevel))
        .apply(
            builder -> {
              for (Method apiMethod : apiMethods()) {
                setMockApiLevelForMethod(apiMethod, methodApiLevel).accept(builder);
              }
            })
        .apply(ApiModelingTestHelper::enableOutliningOfMethods);
  }

  public boolean addToBootClasspath() {
    return parameters.isDexRuntime() && runApiLevel().isGreaterThanOrEqualTo(methodApiLevel);
  }

  @Test
  public void testD8Debug() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
        .setMode(CompilationMode.DEBUG)
        .apply(this::setupTestBuilder)
        .compile()
        .applyIf(addToBootClasspath(), b -> b.addBootClasspathClasses(LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testD8Release() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
        .setMode(CompilationMode.RELEASE)
        .apply(this::setupTestBuilder)
        .compile()
        .applyIf(addToBootClasspath(), b -> b.addBootClasspathClasses(LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .apply(this::setupTestBuilder)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .compile()
        .applyIf(addToBootClasspath(), b -> b.addBootClasspathClasses(LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  public void checkOutput(SingleTestRunResult<?> runResult) {
    if (parameters.isDexRuntime() && runApiLevel().isGreaterThanOrEqualTo(methodApiLevel)) {
      runResult.assertSuccessWithOutputLines(
          "Could not access LibraryClass::addedOn27",
          "LibraryClass::addedOn27",
          "LibraryClass::addedOn27",
          "LibraryCLass::alsoAddedOn27",
          "TestClass::superInvokeOn27",
          "LibraryCLass::superInvokeOn27");
    } else {
      runResult.assertSuccessWithOutputLines("Not calling API");
    }
  }

  // Only present from api level 23.
  public static class LibraryClass {

    protected static void addedOn27() {
      System.out.println("LibraryClass::addedOn27");
    }

    protected void alsoAddedOn27() {
      System.out.println("LibraryCLass::alsoAddedOn27");
    }

    protected void superInvokeOn27() {
      System.out.println("LibraryCLass::superInvokeOn27");
    }
  }

  @NeverClassInline
  public static class /* foo.bar */ TestClass extends LibraryClass {

    @NeverInline
    public static void callAddedOn27() {
      LibraryClass.addedOn27();
    }

    @NeverInline
    public void test() {
      addedOn27();
      LibraryClass libraryClass = this;
      libraryClass.alsoAddedOn27();
      libraryClass.superInvokeOn27();
    }

    @Override
    @NeverInline
    protected void superInvokeOn27() {
      System.out.println("TestClass::superInvokeOn27");
      super.superInvokeOn27();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      if (AndroidBuildVersion.VERSION >= 27) {
        try {
          TestClass.addedOn27();
        } catch (IllegalAccessError accessError) {
          System.out.println("Could not access LibraryClass::addedOn27");
        }
        TestClass.callAddedOn27();
        new TestClass().test();
      } else {
        System.out.println("Not calling API");
      }
    }
  }
}
