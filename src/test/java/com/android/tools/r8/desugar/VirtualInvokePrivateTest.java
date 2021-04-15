// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.DesugarTestConfiguration;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class VirtualInvokePrivateTest extends TestBase implements Opcodes {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimes()
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  public VirtualInvokePrivateTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private boolean isNotDesugaredAndCfRuntimeNewerThanOrEqualToJDK11(
      DesugarTestConfiguration configuration) {
    return DesugarTestConfiguration.isNotDesugared(configuration)
        && parameters.getRuntime().isCf()
        && parameters.getRuntime().asCf().isNewerThanOrEqual(CfVm.JDK11);
  }

  private void inspectNotDesugared(CodeInspector inspector) {
    MethodSubject main = inspector.clazz(TestRunner.class).uniqueMethodWithName("main");
    assertEquals(2, main.streamInstructions().filter(InstructionSubject::isInvokeVirtual).count());
  }

  private void inspectDesugared(CodeInspector inspector) {
    MethodSubject main = inspector.clazz(TestRunner.class).uniqueMethodWithName("main");
    assertEquals(1, main.streamInstructions().filter(InstructionSubject::isInvokeVirtual).count());
  }

  @Test
  public void testReference() throws Exception {
    assumeTrue(parameters.getRuntime().isCf());
    assumeTrue(parameters.getApiLevel().isEqualTo(AndroidApiLevel.B));

    testForJvm()
        .addProgramClassFileData(transformInvokeSpecialToInvokeVirtual())
        .run(parameters.getRuntime(), TestRunner.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  @Test
  public void testDesugar() throws Exception {
    testForDesugaring(parameters)
        .addProgramClassFileData(transformInvokeSpecialToInvokeVirtual())
        .run(parameters.getRuntime(), TestRunner.class)
        .inspectIf(
            this::isNotDesugaredAndCfRuntimeNewerThanOrEqualToJDK11, this::inspectNotDesugared)
        .inspectIf(DesugarTestConfiguration::isDesugared, this::inspectDesugared)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(transformInvokeSpecialToInvokeVirtual())
        .addKeepMainRule(TestRunner.class)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), TestRunner.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  private byte[] transformInvokeSpecialToInvokeVirtual() throws IOException {
    return transformer(TestRunner.class)
        .setVersion(CfVersion.V1_8)
        .transformMethodInsnInMethod(
            "main",
            ((opcode, owner, name, descriptor, isInterface, continuation) -> {
              continuation.visitMethodInsn(
                  name.equals("hello") ? Opcodes.INVOKEVIRTUAL : opcode,
                  owner,
                  name,
                  descriptor,
                  isInterface);
            }))
        .transform();
  }

  public static class TestRunner {
    private String hello() {
      return "Hello, world!";
    }

    public static void main(String[] args) {
      // The private method "hello" is called with invokevirtual.
      System.out.println(new TestRunner().hello());
    }
  }
}
