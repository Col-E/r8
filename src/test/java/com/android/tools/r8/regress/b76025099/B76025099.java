// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b76025099;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.ProguardTestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.regress.b76025099.testclasses.Main;
import com.android.tools.r8.regress.b76025099.testclasses.impl.Impl;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldAccessInstructionSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Iterator;
import java.util.function.Function;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class B76025099 extends TestBase {

  private static final String EXPECTED_OUTPUT = StringUtils.lines(Main.class.getTypeName());

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public B76025099(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testProguardAndD8() throws Exception {
    assumeTrue(isRunProguard());

    ProguardTestCompileResult proguardCompileResult =
        testForProguard()
            .addProgramFiles(ToolHelper.getClassFilesForTestPackage(Main.class.getPackage()))
            .addKeepMainRule(Main.class)
            .addDontObfuscate()
            .compile();

    if (parameters.isDexRuntime()) {
      testForD8()
          .addProgramFiles(proguardCompileResult.outputJar())
          .setMinApi(parameters)
          .compile()
          .inspect(this::verifyFieldAccess)
          .run(parameters.getRuntime(), Main.class)
          .assertSuccessWithOutput(EXPECTED_OUTPUT);
    } else {
      proguardCompileResult
          .run(parameters.getRuntime(), Main.class)
          .assertSuccessWithOutput(EXPECTED_OUTPUT);
    }
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(ToolHelper.getClassFilesForTestPackage(Main.class.getPackage()))
        .addKeepMainRule(Main.class)
        .enableNoAccessModificationAnnotationsForClasses()
        .enableNoAccessModificationAnnotationsForMembers()
        .enableNoVerticalClassMergingAnnotations()
        .addDontObfuscate()
        .setMinApi(parameters)
        .compile()
        .inspect(this::verifyFieldAccess)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private static InstructionSubject findInstructionOrNull(
      Iterator<InstructionSubject> iterator, Function<InstructionSubject, Boolean> predicate) {
    while (iterator.hasNext()) {
      InstructionSubject instruction = iterator.next();
      if (predicate.apply(instruction)) {
        return instruction;
      }
    }
    return null;
  }

  private void verifyFieldAccess(CodeInspector inspector) {
    ClassSubject impl = inspector.clazz(Impl.class);
    assertThat(impl, isPresent());
    MethodSubject init = impl.init("java.lang.String");
    assertThat(init, isPresent());
    Iterator<InstructionSubject> iterator = init.iterateInstructions();

    assertNotNull(findInstructionOrNull(iterator, InstructionSubject::isInvoke));

    InstructionSubject instruction =
        findInstructionOrNull(iterator, InstructionSubject::isInstancePut);
    assertNotNull(instruction);
    FieldAccessInstructionSubject fieldAccessInstruction =
        (FieldAccessInstructionSubject) instruction;
    assertEquals("name", fieldAccessInstruction.name());
    assertTrue(fieldAccessInstruction.holder().is(impl.getDexProgramClass().type.toString()));

    assertNotNull(findInstructionOrNull(iterator, InstructionSubject::isReturnVoid));
  }
}
