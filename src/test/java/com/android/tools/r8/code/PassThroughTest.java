// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.code;

import static junit.framework.Assert.assertSame;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.ArchiveClassFileProvider;
import com.android.tools.r8.CfFrontendExamplesTest;
import com.android.tools.r8.ClassFileResourceProvider;
import com.android.tools.r8.DirectoryClassFileProvider;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PassThroughTest extends TestBase {

  private final String EXPECTED = StringUtils.lines("0", "foo", "0", "foo", "foo");

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean keepDebug;

  @Parameters(name = "{0}, keep-debug: {1}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withCfRuntimes().build(), BooleanUtils.values());
  }

  @Test
  public void testJvm() throws Exception {
    assumeTrue(keepDebug);
    testForJvm(parameters)
        .addProgramClasses(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED);

    // Check that reading the same input is actual matches.
    ClassFileResourceProvider original =
        DirectoryClassFileProvider.fromDirectory(ToolHelper.getClassPathForTests());
    verifyInstructionsForMethodMatchingExpectation(original, "main", true, true);
    verifyInstructionsForMethodMatchingExpectation(original, "exceptionTest", true, true);
  }

  @Test
  public void testR8() throws Exception {
    Path outputJar = temp.newFile("output.jar").toPath();
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addKeepAllClassesRule()
        .enableInliningAnnotations()
        .applyIf(keepDebug, TestShrinkerBuilder::addKeepAllAttributes)
        .compile()
        .writeToZip(outputJar)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED);
    ArchiveClassFileProvider actual = new ArchiveClassFileProvider(outputJar);
    verifyInstructionsForMethodMatchingExpectation(actual, "main", keepDebug, false);
    verifyInstructionsForMethodMatchingExpectation(actual, "exceptionTest", keepDebug, false);
  }

  @Test
  public void testR8ByteCodePassThrough() throws Exception {
    Path outputJar = temp.newFile("output.jar").toPath();
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addKeepAllClassesRule()
        .enableInliningAnnotations()
        .applyIf(keepDebug, TestShrinkerBuilder::addKeepAllAttributes)
        .addOptionsModification(
            internalOptions -> {
              internalOptions.testing.cfByteCodePassThrough =
                  method -> !method.name.toString().equals("<init>");
            })
        .compile()
        .writeToZip(outputJar)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED);
    ArchiveClassFileProvider actual = new ArchiveClassFileProvider(outputJar);
    verifyInstructionsForMethodMatchingExpectation(actual, "main", keepDebug, true);
    verifyInstructionsForMethodMatchingExpectation(actual, "exceptionTest", keepDebug, true);
  }

  private void verifyInstructionsForMethodMatchingExpectation(
      ClassFileResourceProvider actual, String methodName, boolean checkDebug, boolean expectation)
      throws Exception {
    ClassFileResourceProvider original =
        DirectoryClassFileProvider.fromDirectory(ToolHelper.getClassPathForTests());
    String descriptor = DescriptorUtils.javaTypeToDescriptor(Main.class.getTypeName());
    byte[] expectedBytes = CfFrontendExamplesTest.getClassAsBytes(original, descriptor);
    byte[] actualBytes = CfFrontendExamplesTest.getClassAsBytes(actual, descriptor);
    if (!Arrays.equals(expectedBytes, actualBytes)) {
      String expectedString = CfFrontendExamplesTest.asmToString(expectedBytes);
      String actualString = CfFrontendExamplesTest.asmToString(actualBytes);
      verifyInstructionsForMethodMatchingExpectation(
          getMethodInstructions(expectedString, methodName),
          getMethodInstructions(actualString, methodName),
          checkDebug,
          expectation);
    }
  }

  private String getMethodInstructions(String asm, String methodName) {
    int methodIndexStart =
        asm.indexOf(
            "methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_STATIC, \""
                + methodName
                + "\",");
    methodIndexStart = asm.indexOf("methodVisitor.visitCode();", methodIndexStart);
    int methodIndexEnd = asm.indexOf("methodVisitor.visitEnd();", methodIndexStart);
    return asm.substring(methodIndexStart, methodIndexEnd);
  }

  private void verifyInstructionsForMethodMatchingExpectation(
      String originalInstructions,
      String actualInstructions,
      boolean checkDebug,
      boolean expectation) {
    if (checkDebug) {
      // We may rewrite jump instructions, so filter those out.
      originalInstructions = filter(originalInstructions, this::isNotLabelOrJumpInstruction);
      actualInstructions = filter(actualInstructions, this::isNotLabelOrJumpInstruction);
    } else {
      originalInstructions = filter(originalInstructions, this::isNotDebugInstructionOrJump);
      actualInstructions = filter(actualInstructions, this::isNotLabelOrJumpInstruction);
    }
    assertSame(expectation, actualInstructions.equals(originalInstructions));
  }

  private String filter(String instructions, Predicate<String> predicate) {
    return StringUtils.splitLines(instructions).stream()
        .filter(predicate)
        .map(instr -> instr + "\n")
        .collect(Collectors.joining());
  }

  private boolean isNotDebugInstructionOrJump(String instruction) {
    return !(instruction.startsWith("methodVisitor.visitLocalVariable")
        || instruction.startsWith("methodVisitor.visitLabel")
        || instruction.startsWith("Label")
        || instruction.startsWith("methodVisitor.visitLineNumber")
        || instruction.startsWith("methodVisitor.visitJumpInsn"));
  }

  private boolean isNotLabelOrJumpInstruction(String instruction) {
    return !(instruction.startsWith("Label")
        || instruction.startsWith("methodVisitor.visitJumpInsn")
        || instruction.startsWith("methodVisitor.visitLabel"));
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
      System.out.println(phiTest(args.length > 0 ? args[0] : null));
      System.out.println(exceptionTest(args.length > 0 ? args[0] : null));
    }

    @NeverInline
    public static String phiTest(String arg) {
      String result;
      if (arg == null) {
        result = "foo";
      } else {
        result = "bar";
      }
      return result;
    }

    @NeverInline
    public static String exceptionTest(String arg) {
      try {
        return arg.toLowerCase();
      } catch (NullPointerException ignored) {
        return "foo";
      }
    }
  }
}
