// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.devirtualize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ir.optimize.devirtualize.invokeinterface.A;
import com.android.tools.r8.ir.optimize.devirtualize.invokeinterface.A0;
import com.android.tools.r8.ir.optimize.devirtualize.invokeinterface.A1;
import com.android.tools.r8.ir.optimize.devirtualize.invokeinterface.I;
import com.android.tools.r8.ir.optimize.devirtualize.invokeinterface.Main;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InvokeInterfaceToInvokeVirtualTest extends TestBase {

  private final TestParameters parameters;
  private final String EXPECTED_OUTPUT = "0";

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InvokeInterfaceToInvokeVirtualTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(I.class, A.class, A0.class, A1.class, Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void listOfInterface() throws Exception {
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addProgramClasses(I.class, A.class, A0.class, A1.class, Main.class)
            .addKeepMainRule(Main.class)
            .addOptionsModification(
                options ->
                    options.inlinerOptions().enableInliningOfInvokesWithNullableReceivers = false)
            .enableNoHorizontalClassMergingAnnotations()
            .setMinApi(parameters)
            .run(parameters.getRuntime(), Main.class)
            .assertSuccessWithOutputLines(EXPECTED_OUTPUT)
            .inspector();

    ClassSubject clazz = inspector.clazz(Main.class);
    MethodSubject m = clazz.mainMethod();
    // List#add and List#get get devirtualized into ArrayList#add and ArrayList#get.
    assertTrue(m.streamInstructions().noneMatch(InstructionSubject::isInvokeInterface));
    // System.out.println, List#add ~> ArrayList#add, List#get ~> ArrayList#get, I#get ~> A0#get.
    long numOfInvokeVirtual =
        m.streamInstructions().filter(InstructionSubject::isInvokeVirtual).count();
    assertEquals(4, numOfInvokeVirtual);
    // check-cast I ~> check-cast A0.
    long numOfCast = m.streamInstructions().filter(InstructionSubject::isCheckCast).count();
    assertEquals(1, numOfCast);
  }
}
