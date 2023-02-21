// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.proxy;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.shaking.proxy.testclasses.BaseInterface;
import com.android.tools.r8.shaking.proxy.testclasses.Main;
import com.android.tools.r8.shaking.proxy.testclasses.SubClass;
import com.android.tools.r8.shaking.proxy.testclasses.SubInterface;
import com.android.tools.r8.shaking.proxy.testclasses.TestClass;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.InvokeInstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import java.util.List;
import java.util.function.Predicate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ProxiesTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ProxiesTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private void runTest(
      List<String> additionalKeepRules,
      ThrowingConsumer<CodeInspector, RuntimeException> inspection,
      String expectedResult)
      throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(ToolHelper.getClassFilesForTestPackage(Main.class.getPackage()))
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-keep class " + Main.class.getCanonicalName() + " {",
            // Keep x, y and z to avoid them being inlined into main.
            "  private void x(com.android.tools.r8.shaking.proxy.testclasses.BaseInterface);",
            "  private void y(com.android.tools.r8.shaking.proxy.testclasses.SubInterface);",
            "  private void z(com.android.tools.r8.shaking.proxy.testclasses.TestClass);",
            "  private void z(com.android.tools.r8.shaking.proxy.testclasses.SubClass);",
            "}")
        .addKeepRules(additionalKeepRules)
        .addOptionsModification(
            o -> {
              o.enableDevirtualization = false;
            })
        .enableAlwaysInliningAnnotations()
        .addDontObfuscate()
        .setMinApi(parameters)
        .compile()
        .inspect(inspection)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(StringUtils.withNativeLineSeparator(expectedResult));
  }

  private int countInstructionInX(CodeInspector inspector, Predicate<InstructionSubject> invoke) {
    MethodSignature signatureForX =
        new MethodSignature("x", "void", ImmutableList.of(BaseInterface.class.getCanonicalName()));
    MethodSubject method = inspector.clazz(Main.class).method(signatureForX);
    assert method instanceof FoundMethodSubject;
    FoundMethodSubject foundMethod = (FoundMethodSubject) method;
    return (int) Streams.stream(foundMethod.iterateInstructions(invoke)).count();
  }

  private int countInstructionInY(CodeInspector inspector, Predicate<InstructionSubject> invoke) {
    MethodSignature signatureForY =
        new MethodSignature("y", "void", ImmutableList.of(SubInterface.class.getCanonicalName()));
    MethodSubject method = inspector.clazz(Main.class).method(signatureForY);
    assert method instanceof FoundMethodSubject;
    FoundMethodSubject foundMethod = (FoundMethodSubject) method;
    return (int)
        Streams.stream(foundMethod.iterateInstructions(invoke))
            .filter(
                instruction -> {
                  InvokeInstructionSubject invokeInstruction =
                      (InvokeInstructionSubject) instruction;
                  return invokeInstruction.invokedMethod().qualifiedName().endsWith("method");
                })
            .count();
  }

  private int countInstructionInZ(CodeInspector inspector, Predicate<InstructionSubject> invoke) {
    MethodSignature signatureForZ =
        new MethodSignature("z", "void", ImmutableList.of(TestClass.class.getCanonicalName()));
    MethodSubject method = inspector.clazz(Main.class).method(signatureForZ);
    assert method instanceof FoundMethodSubject;
    FoundMethodSubject foundMethod = (FoundMethodSubject) method;
    return (int)
        Streams.stream(foundMethod.iterateInstructions(invoke))
            .filter(
                instruction -> {
                  InvokeInstructionSubject invokeInstruction =
                      (InvokeInstructionSubject) instruction;
                  return invokeInstruction.invokedMethod().qualifiedName().endsWith("method");
                })
            .count();
  }

  private int countInstructionInZSubClass(
      CodeInspector inspector, Predicate<InstructionSubject> invoke) {
    MethodSignature signatureForZ =
        new MethodSignature("z", "void", ImmutableList.of(SubClass.class.getCanonicalName()));
    MethodSubject method = inspector.clazz(Main.class).method(signatureForZ);
    assert method instanceof FoundMethodSubject;
    FoundMethodSubject foundMethod = (FoundMethodSubject) method;
    return (int)
        Streams.stream(foundMethod.iterateInstructions(invoke))
            .filter(
                instruction -> {
                  InvokeInstructionSubject invokeInstruction =
                      (InvokeInstructionSubject) instruction;
                  return invokeInstruction.invokedMethod().qualifiedName().endsWith("method");
                })
            .count();
  }

  private void noInterfaceKept(CodeInspector inspector) {
    // Indirectly assert that method is inlined into x, y and z and that redundant field loads
    // remove invokes.
    assertEquals(0, countInstructionInX(inspector, InstructionSubject::isInvokeInterface));
    assertEquals(0, countInstructionInY(inspector, InstructionSubject::isInvokeInterface));
    assertEquals(0, countInstructionInZ(inspector, InstructionSubject::isInvokeVirtual));
  }

  @Test
  public void testNoInterfaceKept() throws Exception {
    runTest(
        ImmutableList.of(),
        this::noInterfaceKept,
        "TestClass 1\nTestClass 1\nTestClass 1\nEXCEPTION\n");
  }

  private void baseInterfaceKept(CodeInspector inspector) {
    // Indirectly assert that method is not inlined into x.
    assertEquals(3, countInstructionInX(inspector, InstructionSubject::isInvokeInterface));
    // Indirectly assert that method is inlined into y and z and that redundant field loads
    // remove invokes.
    assertEquals(0, countInstructionInY(inspector, InstructionSubject::isInvokeInterface));
    assertEquals(0, countInstructionInZ(inspector, InstructionSubject::isInvokeVirtual));
    assertEquals(0, countInstructionInZSubClass(inspector, InstructionSubject::isInvokeVirtual));
  }

  @Test
  public void testBaseInterfaceKept() throws Exception {
    runTest(
        ImmutableList.of(
            "-keep interface " + BaseInterface.class.getCanonicalName() + " {",
            "  <methods>;",
            "}"),
        this::baseInterfaceKept,
        "TestClass 1\nTestClass 1\nTestClass 1\nProxy\nProxy\nProxy\n"
            + "TestClass 2\nTestClass 2\nTestClass 2\nEXCEPTION\n");
  }

  private void subInterfaceKept(CodeInspector inspector) {
    // Indirectly assert that method is not inlined into x or y.
    assertEquals(3, countInstructionInX(inspector, InstructionSubject::isInvokeInterface));
    assertEquals(3, countInstructionInY(inspector, InstructionSubject::isInvokeInterface));
    // Indirectly assert that method is inlined into x, y and z and that redundant field loads
    // remove invokes.
    assertEquals(0, countInstructionInZ(inspector, InstructionSubject::isInvokeVirtual));
    assertEquals(0, countInstructionInZSubClass(inspector, InstructionSubject::isInvokeVirtual));
  }

  @Test
  public void testSubInterfaceKept() throws Exception {
    runTest(ImmutableList.of(
        "-keep interface " + SubInterface.class.getCanonicalName() + " {",
        "  <methods>;",
        "}"),
        this::subInterfaceKept,
        "TestClass 1\nTestClass 1\nTestClass 1\nProxy\nProxy\nProxy\n" +
        "TestClass 2\nTestClass 2\nTestClass 2\nProxy\nProxy\nProxy\n" +
        "TestClass 3\nTestClass 3\nTestClass 3\n" +
        "TestClass 4\nTestClass 4\nTestClass 4\nSUCCESS\n");
  }

  private void classKept(CodeInspector inspector) {
    // Indirectly assert that method is not inlined into x, y or z.
    assertEquals(3, countInstructionInX(inspector, InstructionSubject::isInvokeInterface));
    assertEquals(3, countInstructionInY(inspector, InstructionSubject::isInvokeInterface));
    assertEquals(3, countInstructionInZ(inspector, InstructionSubject::isInvokeVirtual));
    assertEquals(3, countInstructionInZSubClass(inspector, InstructionSubject::isInvokeVirtual));
  }

  @Test
  public void testClassKept() throws Exception {
    runTest(ImmutableList.of(
        "-keep class " + TestClass.class.getCanonicalName() + " {",
        "  <methods>;",
        "}"),
        this::classKept,
        "TestClass 1\nTestClass 1\nTestClass 1\nProxy\nProxy\nProxy\n" +
        "TestClass 2\nTestClass 2\nTestClass 2\nProxy\nProxy\nProxy\n" +
        "TestClass 3\nTestClass 3\nTestClass 3\n" +
        "TestClass 4\nTestClass 4\nTestClass 4\nSUCCESS\n");
  }

  @Test
  public void testSubClassKept() throws Exception {
    runTest(ImmutableList.of(
        "-keep class " + SubClass.class.getCanonicalName() + " {",
        "  <methods>;",
        "}"),
        this::classKept,
        "TestClass 1\nTestClass 1\nTestClass 1\nProxy\nProxy\nProxy\n" +
        "TestClass 2\nTestClass 2\nTestClass 2\nProxy\nProxy\nProxy\n" +
        "TestClass 3\nTestClass 3\nTestClass 3\n" +
        "TestClass 4\nTestClass 4\nTestClass 4\nSUCCESS\n");
  }
}