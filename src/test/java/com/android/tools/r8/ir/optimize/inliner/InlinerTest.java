// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ir.optimize.inliner.exceptionhandling.ExceptionHandlingTestClass;
import com.android.tools.r8.ir.optimize.inliner.interfaces.InterfaceTargetsTestClass;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InlinerTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testExceptionHandling() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(ExceptionHandlingTestClass.class)
        .addKeepMainRule(ExceptionHandlingTestClass.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClassSubject = inspector.clazz(ExceptionHandlingTestClass.class);
              assertThat(mainClassSubject, isPresent());
              assertThat(
                  mainClassSubject.uniqueMethodWithOriginalName(
                      "inlineeWithNormalExitThatDoesNotThrow"),
                  isAbsent());
              assertThat(
                  mainClassSubject.uniqueMethodWithOriginalName("inlineeWithNormalExitThatThrows"),
                  isAbsent());
              assertThat(
                  mainClassSubject.uniqueMethodWithOriginalName("inlineeWithoutNormalExit"),
                  isAbsent());
            })
        .run(parameters.getRuntime(), ExceptionHandlingTestClass.class)
        .assertSuccessWithOutputLines(
            "Test succeeded: methodWithoutCatchHandlersTest(1)",
            "Test succeeded: methodWithoutCatchHandlersTest(2)",
            "Test succeeded: methodWithoutCatchHandlersTest(3)",
            "Test succeeded: methodWithCatchHandlersTest(1)",
            "Test succeeded: methodWithCatchHandlersTest(2)",
            "Test succeeded: methodWithCatchHandlersTest(3)");
  }

  @Test
  public void testInterfacesWithoutTargets() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassesAndInnerClasses(InterfaceTargetsTestClass.class)
        .addKeepMainRule(InterfaceTargetsTestClass.class)
        .allowAccessModification()
        .addDontObfuscate()
        .noClassInlining()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(InterfaceTargetsTestClass.class);
              assertThat(clazz, isPresent());
              assertThat(clazz.uniqueMethodWithOriginalName("testInterfaceNoImpl"), isAbsent());
              assertThat(clazz.uniqueMethodWithOriginalName("testInterfaceA"), isAbsent());
              assertThat(clazz.uniqueMethodWithOriginalName("testInterfaceB"), isAbsent());
              assertThat(clazz.uniqueMethodWithOriginalName("testInterfaceD"), isAbsent());
              assertThat(clazz.uniqueMethodWithOriginalName("testInterfaceD"), isAbsent());
            })
        .run(parameters.getRuntime(), InterfaceTargetsTestClass.class)
        .assertSuccessWithOutputLines(
            "testInterfaceNoImpl::OK",
            "testInterfaceA::OK",
            "testInterfaceB::OK",
            "testInterfaceC::OK",
            "testInterfaceD::OK");
  }
}
