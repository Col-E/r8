// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ApiLevelBackportsTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    // NOTE: Most of the 'run' invocations below work only because the static configured APIs do not
    // give rise to DEX file versions larger than what can be accepted by VM 9.0.0.
    return getTestParameters().withDexRuntimesStartingFromIncluding(Version.V9_0_0).build();
  }

  public ApiLevelBackportsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void backportSucceedsOnSupportedApiLevel() throws Exception {
    testForD8()
        .addProgramClassFileData(transformTestMathMultiplyExactLongInt())
        .setMinApi(AndroidApiLevel.B)
        .run(parameters.getRuntime(), TestMathMultiplyExactLongInt.class)
        .assertSuccessWithOutputLines("4");
  }

  @Test
  public void backportOfNonPresentMethodOnLatest() throws Exception {
    testForD8()
        .addProgramClassFileData(transformTestMathMultiplyExactLongInt())
        .setMinApi(AndroidApiLevel.LATEST)
        .compile()
        .assertNoMessages()
        .run(parameters.getRuntime(), TestMathMultiplyExactLongInt.class)
        .assertSuccessWithOutputLines("4");
  }

  @Test
  public void backportOfPresentMethodOnLatest() throws Exception {
    D8TestRunResult result =
        testForD8()
            .addProgramClassFileData(transformTestListOf())
            .setMinApi(AndroidApiLevel.LATEST)
            .compile()
            .assertNoMessages()
            .run(parameters.getRuntime(), TestListOf.class);
    if (runtimeHasListOf()) {
      result.assertSuccessWithOutputLines("0");
    } else {
      result.assertFailureWithErrorThatMatches(
          containsString("java.lang.NoSuchMethodError: No static method of()Ljava/util/List;"));
    }
  }

  @Test
  public void warningForFutureNonPlatformBuild() throws Exception {
    testForD8()
        .addProgramClassFileData(transformTestMathMultiplyExactLongInt())
        .setMinApi(AndroidApiLevel.LATEST.getLevel() + 1)
        .compile()
        .assertOnlyWarnings()
        .assertWarningMessageThatMatches(containsString("is not supported by this compiler"))
        .run(parameters.getRuntime(), TestMathMultiplyExactLongInt.class)
        .assertFailureWithErrorThatMatches(
            containsString("java.lang.NoSuchMethodError: No static method multiplyExact(JI)J"));
  }

  @Test
  public void noWarningForPlatformBuild() throws Exception {
    testForD8()
        .addProgramClassFileData(transformTestMathMultiplyExactLongInt())
        .setMinApi(AndroidApiLevel.magicApiLevelUsedByAndroidPlatformBuild)
        .run(parameters.getRuntime(), TestMathMultiplyExactLongInt.class)
        .assertFailureWithErrorThatMatches(
            containsString("java.lang.NoSuchMethodError: No static method multiplyExact(JI)J"));
  }

  // Test class for using: List List.of()
  // Introduced in Android R.

  boolean runtimeHasListOf() {
    return parameters
        .getRuntime()
        .asDex()
        .getMinApiLevel()
        .isGreaterThanOrEqualTo(AndroidApiLevel.R);
  }

  byte[] transformTestListOf() throws Exception {
    return transformer(TestListOf.class)
        .transformMethodInsnInMethod(
            "main",
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              if (name.equals("List_of")) {
                visitor.visitMethodInsn(opcode, "java/util/List", "of", descriptor, isInterface);
              } else {
                visitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              }
            })
        .transform();
  }

  static class TestListOf {
    public static List List_of() {
      throw null;
    }

    public static void main(String[] args) {
      System.out.println(List_of().size());
    }
  }

  // Test class for the method: long Math.multiplyExact(long, int)
  // Not present on any currently known Android platforms.

  boolean runtimeHasMathMultiplyExactLongInt() {
    // NOTE: This may change with a future release.
    return false;
  }

  byte[] transformTestMathMultiplyExactLongInt() throws Exception {
    return transformer(TestMathMultiplyExactLongInt.class)
        .transformMethodInsnInMethod(
            "main",
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              if (name.equals("Math_multiplyExact")) {
                visitor.visitMethodInsn(
                    opcode, "java/lang/Math", "multiplyExact", descriptor, isInterface);
              } else {
                visitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              }
            })
        .transform();
  }

  static class TestMathMultiplyExactLongInt {
    public static long Math_multiplyExact(long l, int i) {
      throw null;
    }

    public static void main(String[] args) {
      System.out.println(Math_multiplyExact(2L, 2));
    }
  }
}
