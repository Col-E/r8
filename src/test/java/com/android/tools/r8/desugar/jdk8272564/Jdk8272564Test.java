// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.jdk8272564;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.examples.jdk18.jdk8272564.Jdk8272564;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Jdk8272564Test extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK20)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  @Parameter(0)
  public TestParameters parameters;

  // With the fix for JDK-8272564 there are no invokevirtual instructions.
  private void assertJdk8272564FixedCode(CodeInspector inspector) {
    assertTrue(
        inspector
            .clazz(Jdk8272564.Main.typeName())
            .uniqueMethodWithOriginalName("f")
            .streamInstructions()
            .noneMatch(InstructionSubject::isInvokeVirtual));
    assertTrue(
        inspector
            .clazz(Jdk8272564.Main.typeName())
            .uniqueMethodWithOriginalName("g")
            .streamInstructions()
            .noneMatch(InstructionSubject::isInvokeVirtual));
  }

  // Without the fix for JDK-8272564 there is one invokeinterface and 2 invokevirtual instructions.
  private void assertJdk8272564NotFixedCode(
      CodeInspector inspector, int invokeVirtualCount, int getClassCount) {
    assertEquals(
        1,
        inspector
            .clazz(Jdk8272564.Main.typeName())
            .uniqueMethodWithOriginalName("f")
            .streamInstructions()
            .filter(InstructionSubject::isInvokeInterface)
            .count());
    assertEquals(
        2,
        inspector
            .clazz(Jdk8272564.Main.typeName())
            .uniqueMethodWithOriginalName("f")
            .streamInstructions()
            .filter(InstructionSubject::isInvokeVirtual)
            .count());
    assertEquals(
        2,
        inspector
            .clazz(Jdk8272564.Main.typeName())
            .uniqueMethodWithOriginalName("g")
            .streamInstructions()
            .filter(InstructionSubject::isInvokeInterface)
            .count());
    assertEquals(
        2,
        inspector
            .clazz(Jdk8272564.Main.typeName())
            .uniqueMethodWithOriginalName("g")
            .streamInstructions()
            .filter(InstructionSubject::isInvokeInterface)
            .count());
    assertEquals(
        invokeVirtualCount,
        inspector
            .clazz(Jdk8272564.Main.typeName())
            .uniqueMethodWithOriginalName("g")
            .streamInstructions()
            .filter(InstructionSubject::isInvokeVirtual)
            .count());
    assertEquals(
        getClassCount,
        inspector
            .clazz(Jdk8272564.Main.typeName())
            .uniqueMethodWithOriginalName("g")
            .streamInstructions()
            .filter(InstructionSubject::isInvoke)
            .filter(instruction -> instruction.getMethod().getName().toString().equals("getClass"))
            .count());
  }

  private void assertJdk8272564NotFixedCode(CodeInspector inspector) {
    assertJdk8272564NotFixedCode(inspector, 22, 3);
  }

  private void assertJdk8272564NotFixedCodeR8(CodeInspector inspector) {
    assertJdk8272564NotFixedCode(inspector, 19, 0);
  }

  private boolean isDefaultCfParameters() {
    return parameters.isCfRuntime() && parameters.getApiLevel().equals(AndroidApiLevel.B);
  }

  @Test
  // See https://bugs.openjdk.java.net/browse/JDK-8272564.
  public void testJdk8272564Compiler() throws Exception {
    assumeTrue(isDefaultCfParameters());
    // Ensure that the test is running with CF input from fixing JDK-8272564.
    assertJdk8272564FixedCode(new CodeInspector(Jdk8272564.jar()));
  }

  @Test
  public void testJvm() throws Exception {
    assumeTrue(isDefaultCfParameters());
    testForJvm(parameters)
        .addRunClasspathFiles(Jdk8272564.jar())
        .run(parameters.getRuntime(), Jdk8272564.Main.typeName())
        .assertSuccess();
  }

  @Test
  public void testD8() throws Exception {
    testForDesugaring(parameters)
        .addProgramFiles(Jdk8272564.jar())
        .run(parameters.getRuntime(), Jdk8272564.Main.typeName())
        .applyIf(
            parameters.isDexRuntime() && parameters.getApiLevel().isLessThan(AndroidApiLevel.S),
            b -> b.inspect(this::assertJdk8272564NotFixedCode),
            b -> b.inspect(this::assertJdk8272564FixedCode))
        .assertSuccess();
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    // The R8 lens code rewriter rewrites to the code prior to fixing JDK-8272564.
    testForR8(parameters.getBackend())
        .addProgramFiles(Jdk8272564.jar())
        .setMinApi(parameters)
        .noTreeShaking()
        .addKeepClassAndMembersRules(Jdk8272564.Main.typeName())
        .run(parameters.getRuntime(), Jdk8272564.Main.typeName())
        .inspect(this::assertJdk8272564NotFixedCodeR8)
        .assertSuccess();
  }
}
