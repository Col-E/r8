// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForDefaultInstanceInitializer;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.lang.reflect.Constructor;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelObjectInitTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    boolean addToClassPath =
        parameters.isDexRuntime()
            && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.L);
    Constructor<LibraryClass> declaredConstructor =
        LibraryClass.class.getDeclaredConstructor(int.class);
    MethodReference shouldBeL =
        Reference.methodFromMethod(Main.class.getDeclaredMethod("shouldBeL"));
    MethodReference shouldBeN =
        Reference.methodFromMethod(Main.class.getDeclaredMethod("shouldBeN"));
    testForR8(parameters.getBackend())
        .addProgramClassFileData(
            transformer(Main.class)
                // We replace java.lang.Object.toString with LibraryClass.toString
                .transformMethodInsnInMethod(
                    "main",
                    (opcode, owner, name, descriptor, isInterface, visitor) -> {
                      if (name.equals("toString") && owner.equals(binaryName(Object.class))) {
                        visitor.visitMethodInsn(
                            opcode, binaryName(LibraryClass.class), name, descriptor, isInterface);
                      } else {
                        visitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                      }
                    })
                .transform())
        .addLibraryClasses(LibraryClass.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .apply(ApiModelingTestHelper::disableOutliningAndStubbing)
        .apply(setMockApiLevelForClass(LibraryClass.class, AndroidApiLevel.L))
        .apply(setMockApiLevelForMethod(declaredConstructor, AndroidApiLevel.L))
        .apply(setMockApiLevelForDefaultInstanceInitializer(LibraryClass.class, AndroidApiLevel.N))
        .apply(
            ApiModelingTestHelper.addTracedApiReferenceLevelCallBack(
                (methodReference, apiLevel) -> {
                  AndroidApiLevel currentLevel =
                      parameters.isCfRuntime() ? AndroidApiLevel.B : parameters.getApiLevel();
                  if (methodReference.equals(shouldBeL)) {
                    Assert.assertEquals(AndroidApiLevel.L.max(currentLevel), apiLevel);
                  }
                  if (methodReference.equals(shouldBeN)) {
                    Assert.assertEquals(AndroidApiLevel.N.max(currentLevel), apiLevel);
                  }
                }))
        .addKeepMainRule(Main.class)
        .addAndroidBuildVersion()
        .enableInliningAnnotations()
        .compile()
        .applyIf(addToClassPath, b -> b.addRunClasspathClasses(LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLinesIf(!addToClassPath, "Old")
        .assertSuccessWithOutputLinesIf(addToClassPath, "New");
  }

  public static class LibraryClass {

    public LibraryClass() {
      // Default constructor
    }

    public LibraryClass(int i) {
      // Non default constructor.
    }
  }

  public static class Main {

    @NeverInline
    public static void shouldBeL() {
      LibraryClass libraryClass = new LibraryClass(1);
    }

    @NeverInline
    public static void shouldBeN() {
      if (new LibraryClass().toString().equals("FOO")) {
        throw new RuntimeException("Unexpected toString value");
      }
    }

    public static void main(String[] args) {
      if (AndroidBuildVersion.VERSION >= 21) {
        shouldBeL();
        shouldBeN();
        System.out.println("New");
      } else {
        System.out.println("Old");
      }
    }
  }
}
