// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForDefaultInstanceInitializer;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
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

  @Test
  public void testR8() throws Exception {
    // TODO(b/197078995): Make this work on 12+.
    assumeFalse(
        parameters.isDexRuntime()
            && parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V12_0_0));
    Method[] apiMethods =
        new Method[] {
          LibraryClass.class.getDeclaredMethod("addedOn27"),
          LibraryClass.class.getDeclaredMethod("alsoAddedOn27"),
          LibraryClass.class.getDeclaredMethod("superInvokeOn27")
        };
    AndroidApiLevel runApiLevel =
        parameters.isCfRuntime()
            ? AndroidApiLevel.B
            : parameters.getRuntime().maxSupportedApiLevel();
    boolean willInvokeLibraryMethods =
        parameters.isDexRuntime() && runApiLevel.isGreaterThanOrEqualTo(methodApiLevel);
    testForR8(parameters.getBackend())
        .addLibraryClasses(LibraryClass.class)
        .addLibraryFiles(
            parameters.isCfRuntime()
                ? ToolHelper.getJava8RuntimeJar()
                : ToolHelper.getFirstSupportedAndroidJar(runApiLevel))
        .addProgramClassFileData(
            transformer(TestClass.class).setClassDescriptor(TESTCLASS_DESCRIPTOR).transform(),
            transformer(Main.class)
                .replaceClassDescriptorInMethodInstructions(
                    descriptor(TestClass.class), TESTCLASS_DESCRIPTOR)
                .transform())
        .addKeepMainRule(Main.class)
        .setMinApi(AndroidApiLevel.B)
        .addAndroidBuildVersion(runApiLevel)
        .apply(setMockApiLevelForClass(LibraryClass.class, classApiLevel))
        .apply(setMockApiLevelForDefaultInstanceInitializer(LibraryClass.class, classApiLevel))
        .apply(
            builder -> {
              for (Method apiMethod : apiMethods) {
                setMockApiLevelForMethod(apiMethod, methodApiLevel).accept(builder);
              }
            })
        .apply(ApiModelingTestHelper::enableOutliningOfMethods)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .compile()
        .applyIf(willInvokeLibraryMethods, b -> b.addBootClasspathClasses(LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLinesIf(!willInvokeLibraryMethods, "Not calling API")
        .assertSuccessWithOutputLinesIf(
            willInvokeLibraryMethods,
            "Could not access LibraryClass::addedOn27",
            "LibraryClass::addedOn27",
            "LibraryClass::addedOn27",
            "LibraryCLass::alsoAddedOn27",
            "TestClass::superInvokeOn27",
            "LibraryCLass::superInvokeOn27");
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
