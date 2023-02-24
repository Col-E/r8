// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jasmin;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.jasmin.JasminBuilder.ClassFileVersion;
import com.android.tools.r8.utils.codeinspector.CfInstructionSubject;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.Iterator;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Regress65432240 extends JasminTestBase {

  private static final String EXPECTED_OUTPUT = "00";

  private static List<byte[]> programClassFileData;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @BeforeClass
  public static void setup() throws Exception {
    JasminBuilder builder = new JasminBuilder(ClassFileVersion.JDK_1_4);
    JasminBuilder.ClassBuilder clazz = builder.addClass("Test");

    clazz.addStaticMethod(
        "test1",
        ImmutableList.of("I"),
        "I",
        ".limit stack 3",
        ".limit locals 2",
        "  iload 0",
        "  ifne L2",
        "L1:",
        "  iconst_0",
        "  ireturn",
        "L2:",
        "  iload 0",
        "  iload 0",
        "  iconst_1",
        "  isub",
        "  invokestatic Test/test1(I)I",
        "  iadd",
        "  ireturn");

    clazz.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  iconst_0",
        "  invokestatic Test/test1(I)I",
        "  invokestatic java/lang/Integer/toString(I)Ljava/lang/String;",
        "  invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  iconst_0",
        "  invokestatic Test/test1(I)I",
        "  invokestatic java/lang/Integer/toString(I)Ljava/lang/String;",
        "  invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V",
        "  return");

    programClassFileData = builder.buildClasses();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClassFileData(programClassFileData)
        .run(parameters.getRuntime(), "Test")
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testConstantNotIntoEntryBlock() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(programClassFileData)
        .addDontObfuscate()
        .addDontShrink()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject inspectedClass = inspector.clazz("Test");
              MethodSubject method = inspectedClass.uniqueMethodWithOriginalName("test1");
              assertTrue(method.isPresent());
              Iterator<InstructionSubject> iterator = method.iterateInstructions();
              InstructionSubject instruction = null;
              boolean found = false;
              while (iterator.hasNext()) {
                instruction = iterator.next();
                if (!(instruction instanceof CfInstructionSubject)
                    || !((CfInstructionSubject) instruction).isLoad()) {
                  found = true;
                  break;
                }
              }
              assertTrue(found && instruction.isIfNez());
            })
        .run(parameters.getRuntime(), "Test")
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }
}
