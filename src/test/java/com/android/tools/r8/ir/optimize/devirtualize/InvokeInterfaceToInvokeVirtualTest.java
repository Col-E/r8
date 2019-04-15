// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.devirtualize;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ir.optimize.devirtualize.invokeinterface.A;
import com.android.tools.r8.ir.optimize.devirtualize.invokeinterface.A0;
import com.android.tools.r8.ir.optimize.devirtualize.invokeinterface.A1;
import com.android.tools.r8.ir.optimize.devirtualize.invokeinterface.I;
import com.android.tools.r8.ir.optimize.devirtualize.invokeinterface.Main;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.Streams;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InvokeInterfaceToInvokeVirtualTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  public InvokeInterfaceToInvokeVirtualTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void listOfInterface() throws Exception {
    String expectedOutput = StringUtils.lines("0");

    if (parameters.isCfRuntime()) {
      testForJvm()
          .addTestClasspath()
          .run(parameters.getRuntime(), Main.class)
          .assertSuccessWithOutput(expectedOutput);
    }

    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addProgramClasses(I.class, A.class, A0.class, A1.class, Main.class)
            .addKeepMainRule(Main.class)
            .addOptionsModification(
                options -> options.enableInliningOfInvokesWithNullableReceivers = false)
            .setMinApi(parameters.getRuntime())
            .run(parameters.getRuntime(), Main.class)
            .assertSuccessWithOutput(expectedOutput)
            .inspector();

    ClassSubject clazz = inspector.clazz(Main.class);
    MethodSubject m = clazz.method(CodeInspector.MAIN);
    long numOfInvokeInterface =
        Streams.stream(m.iterateInstructions(InstructionSubject::isInvokeInterface)).count();
    // List#add, List#get
    assertEquals(2, numOfInvokeInterface);
    long numOfInvokeVirtual =
        Streams.stream(m.iterateInstructions(InstructionSubject::isInvokeVirtual)).count();
    // System.out.println, I#get ~> A0#get
    assertEquals(2, numOfInvokeVirtual);
    long numOfCast = Streams.stream(m.iterateInstructions(InstructionSubject::isCheckCast)).count();
    // check-cast I ~> check-cast A0
    assertEquals(1, numOfCast);
  }
}
