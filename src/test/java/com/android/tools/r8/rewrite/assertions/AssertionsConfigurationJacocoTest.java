// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.assertions;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.AssertionsConfiguration;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.rewrite.assertions.testclasses.A;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class AssertionsConfigurationJacocoTest extends TestBase implements Opcodes {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public AssertionsConfigurationJacocoTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private void checkAssertionCodeEnabled(CodeInspector inspector) {
    AssertionsCheckerUtils.checkAssertionCodeEnabled(inspector.clazz(A.class), "m");
  }

  @Test
  public void testD8() throws Exception {
    Assume.assumeTrue(parameters.isDexRuntime());
    testForD8()
        .addProgramClasses(TestClass.class, MockJacocoInit.class)
        .addProgramClassFileData(transformClassWithJacocoInstrumentation(A.class))
        .setMinApi(parameters)
        .addAssertionsConfiguration(AssertionsConfiguration.Builder::compileTimeEnableAllAssertions)
        .run(parameters.getRuntime(), TestClass.class)
        .inspect(this::checkAssertionCodeEnabled)
        .assertSuccessWithOutputLines("AssertionError in A");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class, MockJacocoInit.class)
        .addProgramClassFileData(transformClassWithJacocoInstrumentation(A.class))
        .addKeepMainRule(TestClass.class)
        .addKeepClassAndMembersRules(A.class, MockJacocoInit.class)
        .setMinApi(parameters)
        .addAssertionsConfiguration(AssertionsConfiguration.Builder::compileTimeEnableAllAssertions)
        .run(parameters.getRuntime(), TestClass.class)
        .inspect(this::checkAssertionCodeEnabled)
        .assertSuccessWithOutputLines("AssertionError in A");
  }

  private byte[] transformClassWithJacocoInstrumentation(Class<?> clazz) throws IOException {
    // Instrument the class with Jacoco.
    Path dir = temp.newFolder().toPath();
    runJacoco(ToolHelper.getClassFileForTestClass(clazz), dir);
    // Rewrite the invocation of Jacoco initialization (getProbes invocation) for class with a Mock.
    Path jacoco =
        dir.resolve(
            A.class.getTypeName().substring(clazz.getPackage().getName().length() + 1) + ".class");
    return transformer(jacoco, Reference.classFromClass(clazz))
        .transformMethodInsnInMethod(
            "$jacocoInit",
            (opcode, owner, name, descriptor, isInterface, continuation) -> {
              assertEquals(INVOKESTATIC, opcode);
              assertEquals("getProbes", name);
              continuation.visitMethodInsn(
                  INVOKESTATIC,
                  DescriptorUtils.getClassBinaryName(MockJacocoInit.class),
                  "getProbes",
                  descriptor,
                  isInterface);
            })
        .transform();
  }

  private void runJacoco(Path input, Path outdir) throws IOException {
    List<String> cmdline = new ArrayList<>();
    cmdline.add(TestRuntime.getSystemRuntime().asCf().getJavaExecutable().toString());
    cmdline.add("-jar");
    cmdline.add(ToolHelper.JACOCO_CLI.toString());
    cmdline.add("instrument");
    cmdline.add(input.toString());
    cmdline.add("--dest");
    cmdline.add(outdir.toString());
    ProcessBuilder builder = new ProcessBuilder(cmdline);
    ProcessResult javacResult = ToolHelper.runProcess(builder);
    assertEquals(javacResult.toString(), 0, javacResult.exitCode);
  }

  public static class MockJacocoInit {

    public static boolean[] getProbes(long a, String b, int c) {
      // For the test class A an array of size 6 is sufficient.
      return new boolean[6];
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      try {
        A.m();
      } catch (AssertionError e) {
        System.out.println("AssertionError in A");
      }
    }
  }

  // The test class A cannot be an inner class of AssertionsConfigurationJacocoTest, as then the
  // desiredAssertionStatus() on the outer AssertionsConfigurationJacocoTest will be used and
  // and that is not part of the input.
}
