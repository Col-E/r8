// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.proguard;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.io.IOException;
import org.hamcrest.CoreMatchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// See b/37324358 for reference.
@RunWith(Parameterized.class)
public class RemovedAndroidApiTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withApiLevel(AndroidApiLevel.B).build();
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.LATEST))
        .addProgramClassFileData(getClassUsingRemovedApi())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatMatches(
            CoreMatchers.anyOf(
                containsString("java.lang.ClassNotFoundException: android.util.FloatMath"),
                containsString(
                    "java.lang.ClassNotFoundException: Didn't find class"
                        + " \"android.util.FloatMath\"")));
  }

  @Test
  public void testProguard() throws Exception {
    parameters.assumeR8TestParameters();
    try {
      testForProguard(ProguardVersion.V7_0_0)
          .addProgramClassFileData(getClassUsingRemovedApi())
          .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.LATEST))
          .addKeepMainRule(TestClass.class)
          .compile();
    } catch (CompilationFailedException e) {
      assertThat(
          e.getMessage(),
          containsString(
              "can't find referenced method 'float floor(float)'"
                  + " in library class android.util.FloatMath"));
    }
  }

  private byte[] getClassUsingRemovedApi() throws IOException {
    return transformer(TestClass.class)
        .transformMethodInsnInMethod(
            "main",
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              if (name.equals("floor")) {
                // The class android.util.FloatMath is still in android.jar, but without any
                // methods.
                // Methods where removed from API level 23.
                visitor.visitMethodInsn(
                    opcode, "android/util/FloatMath", "floor", descriptor, isInterface);
              } else {
                visitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              }
            })
        .transform();
  }

  static class TestClass {
    public static float floor(float f) {
      throw new RuntimeException("Stub");
    }

    public static void main(String[] args) {
      System.out.println(floor(1.234f));
    }
  }
}
