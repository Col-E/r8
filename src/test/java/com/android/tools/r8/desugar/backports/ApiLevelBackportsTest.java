// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import static org.hamcrest.core.StringContains.containsString;

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
        .applyIf(
            runtimeHasParseIntWithFourArgs(),
            r -> r.assertSuccessWithOutputLines("4"),
            r ->
                r.assertFailureWithErrorThatMatches(
                    containsString(
                        "java.lang.NoSuchMethodError: No static method"
                            + " parseInt(Ljava/lang/CharSequence;III)I")));
  }

  @Test
  public void backportOfPresentMethodOnLatest() throws Exception {
    testForD8()
        .addProgramClassFileData(transformTestListOf())
        .setMinApi(AndroidApiLevel.LATEST)
        .compile()
        .assertNoMessages()
        .run(parameters.getRuntime(), TestListOf.class)
        .applyIf(
            runtimeHasListOf(),
            r -> r.assertSuccessWithOutputLines("0"),
            r ->
                r.assertFailureWithErrorThatMatches(
                    containsString(
                        "java.lang.NoSuchMethodError: No static method of()Ljava/util/List;")));
  }

  @Test
  public void warningForFutureNonPlatformBuild() throws Exception {
    testForD8()
        .addProgramClassFileData(transformTestMathMultiplyExactLongInt())
        .setMinApi(AndroidApiLevel.UNKNOWN.getLevel())
        .compile()
        .assertOnlyWarnings()
        .assertWarningMessageThatMatches(containsString("is not supported by this compiler"))
        .run(parameters.getRuntime(), TestMathMultiplyExactLongInt.class)
        .applyIf(
            runtimeHasParseIntWithFourArgs(),
            r -> r.assertSuccessWithOutputLines("4"),
            r ->
                r.assertFailureWithErrorThatMatches(
                    containsString(
                        "java.lang.NoSuchMethodError: No static method"
                            + " parseInt(Ljava/lang/CharSequence;III)I")));
  }

  @Test
  public void noWarningForPlatformBuild() throws Exception {
    testForD8()
        .addProgramClassFileData(transformTestMathMultiplyExactLongInt())
        .setMinApi(AndroidApiLevel.ANDROID_PLATFORM_CONSTANT)
        .compile()
        .assertNoMessages()
        .run(parameters.getRuntime(), TestMathMultiplyExactLongInt.class)
        .applyIf(
            parameters.getDexRuntimeVersion().isOlderThan(Version.V13_0_0),
            b ->
                b.assertFailureWithErrorThatMatches(
                    containsString(
                        "java.lang.NoSuchMethodError: No static method"
                            + " parseInt(Ljava/lang/CharSequence;III)I")),
            b -> b.assertSuccessWithOutputLines("4"));
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

  boolean runtimeHasParseIntWithFourArgs() {
    return parameters
        .getRuntime()
        .asDex()
        .getMinApiLevel()
        .isGreaterThanOrEqualTo(AndroidApiLevel.T);
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
              if (name.equals("Integer_parseInt")) {
                visitor.visitMethodInsn(
                    opcode, "java/lang/Integer", "parseInt", descriptor, isInterface);
              } else {
                visitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              }
            })
        .transform();
  }

  static class TestMathMultiplyExactLongInt {
    public static int Integer_parseInt(CharSequence s, int beginIndex, int endIndex, int radix) {
      throw null;
    }

    public static void main(String[] args) {
      System.out.println(Integer_parseInt("4", 0, 1, 10));
    }
  }
}
