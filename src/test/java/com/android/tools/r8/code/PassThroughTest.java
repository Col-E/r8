// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.code;

import static junit.framework.Assert.assertSame;

import com.android.tools.r8.ArchiveClassFileProvider;
import com.android.tools.r8.CfFrontendExamplesTest;
import com.android.tools.r8.ClassFileResourceProvider;
import com.android.tools.r8.DirectoryClassFileProvider;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PassThroughTest extends TestBase {

  private final String EXPECTED = StringUtils.lines("0", "foo", "0");

  private final TestParameters parameters;
  private final boolean keepDebug;

  @Parameters(name = "{0}, keep-debug: {1}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withCfRuntimes().build(), BooleanUtils.values());
  }

  public PassThroughTest(TestParameters parameters, boolean keepDebug) {
    this.parameters = parameters;
    this.keepDebug = keepDebug;
  }

  @Test
  public void testJmv() throws Exception {
    CodeInspector inspector =
        testForJvm()
            .addProgramClasses(Main.class)
            .run(parameters.getRuntime(), Main.class)
            .assertSuccessWithOutput(EXPECTED)
            .inspector();
    // Check that reading the same input is actual matches.
    ClassFileResourceProvider original =
        DirectoryClassFileProvider.fromDirectory(ToolHelper.getClassPathForTests());
    verifyInstructionsForMainMatchingExpectation(original, true, true);
  }

  @Test
  public void testR8() throws Exception {
    Path outputJar = temp.newFile("output.jar").toPath();
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .ifTrue(keepDebug, TestShrinkerBuilder::addKeepAllAttributes)
        .compile()
        .writeToZip(outputJar)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED);
    verifyInstructionsForMainMatchingExpectation(
        new ArchiveClassFileProvider(outputJar), keepDebug, false);
  }

  @Test
  public void testR8ByteCodePassThrough() throws Exception {
    Path outputJar = temp.newFile("output.jar").toPath();
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .ifTrue(keepDebug, TestShrinkerBuilder::addKeepAllAttributes)
        .addOptionsModification(
            internalOptions ->
                internalOptions.testing.cfByteCodePassThrough =
                    method -> method.method.name.toString().equals("main"))
        .compile()
        .writeToZip(outputJar)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED);
    verifyInstructionsForMainMatchingExpectation(
        new ArchiveClassFileProvider(outputJar), keepDebug, true);
  }

  private void verifyInstructionsForMainMatchingExpectation(
      ClassFileResourceProvider actual, boolean checkDebug, boolean expectation) throws Exception {
    ClassFileResourceProvider original =
        DirectoryClassFileProvider.fromDirectory(ToolHelper.getClassPathForTests());
    String descriptor = DescriptorUtils.javaTypeToDescriptor(Main.class.getTypeName());
    byte[] expectedBytes = CfFrontendExamplesTest.getClassAsBytes(original, descriptor);
    byte[] actualBytes = CfFrontendExamplesTest.getClassAsBytes(actual, descriptor);
    if (!Arrays.equals(expectedBytes, actualBytes)) {
      String expectedString = CfFrontendExamplesTest.asmToString(expectedBytes);
      String actualString = CfFrontendExamplesTest.asmToString(actualBytes);
      verifyInstructionsForMainMatchingExpectation(
          getMethodInstructions(expectedString),
          getMethodInstructions(actualString),
          checkDebug,
          expectation);
    }
  }

  private String getMethodInstructions(String asm) {
    int methodIndexStart =
        asm.indexOf(
            "methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_STATIC, \"main\","
                + " \"([Ljava/lang/String;)V\", null, null);");
    int methodIndexEnd = asm.indexOf("}", methodIndexStart);
    return asm.substring(methodIndexStart, methodIndexEnd);
  }

  private void verifyInstructionsForMainMatchingExpectation(
      String originalInstructions,
      String actualInstructions,
      boolean checkDebug,
      boolean expectation) {
    if (!checkDebug) {
      originalInstructions =
          StringUtils.splitLines(originalInstructions).stream()
              .filter(this::isNotDebugInstruction)
              .map(instr -> instr + "\n")
              .collect(Collectors.joining());
    }
    assertSame(expectation, actualInstructions.equals(originalInstructions));
  }

  private boolean isNotDebugInstruction(String instruction) {
    return !(instruction.startsWith("methodVisitor.visitLocalVariable")
        || instruction.startsWith("methodVisitor.visitLabel")
        || instruction.startsWith("Label")
        || instruction.startsWith("methodVisitor.visitLineNumber"));
  }

  public static class Main {

    public static void main(String[] args) {
      int i = 0;
      System.out.println(i);
      int j = 0;
      String foo = "foo";
      // Keep the false to have R8 remove it.
      if (false) {
        System.out.println(foo);
      }
      System.out.println(foo);
      System.out.println(j);
    }
  }
}
