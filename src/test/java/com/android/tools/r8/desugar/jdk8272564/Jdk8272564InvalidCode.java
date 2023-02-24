// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.jdk8272564;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class Jdk8272564InvalidCode extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimes()
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  @Parameter(0)
  public TestParameters parameters;

  private boolean isDefaultCfParameters() {
    return parameters.isCfRuntime() && parameters.getApiLevel().equals(AndroidApiLevel.B);
  }

  @Test
  public void testRuntime() throws Exception {
    assumeTrue(isDefaultCfParameters());
    testForJvm(parameters)
        .addProgramClasses(I.class)
        .addProgramClassFileData(getTransformedClass())
        .run(parameters.getRuntime(), A.class)
        .assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class);
  }

  @Test
  public void testDesugaring() throws Exception {
    assumeTrue(parameters.isDexRuntime() || isDefaultCfParameters());
    testForDesugaring(parameters)
        .addProgramClasses(I.class)
        .addProgramClassFileData(getTransformedClass())
        .run(parameters.getRuntime(), A.class)
        .applyIf(
            parameters.isDexRuntime()
                && parameters.asDexRuntime().getVersion().isOlderThanOrEqual(Version.V4_4_4),
            r -> r.assertFailureWithErrorThatThrows(NoSuchMethodError.class),
            r -> r.assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class));
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    // The R8 lens code rewriter rewrites to the code prior to fixing JDK-8272564.
    testForR8(parameters.getBackend())
        .addProgramClasses(I.class)
        .addProgramClassFileData(getTransformedClass())
        .setMinApi(parameters)
        .addKeepMainRule(A.class)
        .addOptionsModification(options -> options.testing.allowInvokeErrors = true)
        .run(parameters.getRuntime(), A.class)
        .applyIf(
            parameters.isDexRuntime()
                && parameters.asDexRuntime().getVersion().isOlderThanOrEqual(Version.V4_4_4),
            r -> r.assertFailureWithErrorThatThrows(NoSuchMethodError.class),
            r -> r.assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class));
  }

  private byte[] getTransformedClass() throws Exception {
    return transformer(A.class)
        .transformMethodInsnInMethod(
            "main",
            ((opcode, owner, name, descriptor, isInterface, continuation) -> {
              continuation.visitMethodInsn(
                  name.equals("hashCode") ? Opcodes.INVOKEINTERFACE : opcode,
                  // javac generates java.lang.object as holder, change it to A.
                  name.equals("hashCode") ? DescriptorUtils.getClassBinaryName(A.class) : owner,
                  name,
                  descriptor,
                  name.equals("hashCode") || isInterface);
            }))
        .transform();
  }

  interface I {}

  static class A implements I {

    public static void main(String[] args) {
      new A().hashCode();
    }
  }
}
