// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.ifs;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IfThrowNullPointerExceptionTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection params() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public IfThrowNullPointerExceptionTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
        .addInnerClasses(IfThrowNullPointerExceptionTest.class)
        .release()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Caught NPE", "Caught NPE");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(IfThrowNullPointerExceptionTest.class)
        .addKeepClassAndMembersRules(TestClass.class)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Caught NPE", "Caught NPE");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());

    for (String methodName : ImmutableList.of("testThrowNPE", "testThrowNull")) {
      MethodSubject methodSubject = classSubject.uniqueMethodWithName(methodName);
      assertThat(methodSubject, isPresent());

      IRCode code = methodSubject.buildIR();
      assertEquals(1, code.blocks.size());

      BasicBlock entryBlock = code.entryBlock();
      assertEquals(3, entryBlock.getInstructions().size());
      assertTrue(entryBlock.getInstructions().getFirst().isArgument());
      assertTrue(entryBlock.getInstructions().getLast().isReturn());

      Instruction nullCheckInstruction = entryBlock.getInstructions().get(1);
      if (parameters.isDexRuntime() && parameters.getApiLevel().isLessThan(AndroidApiLevel.K)) {
        assertTrue(nullCheckInstruction.isInvokeVirtual());
        assertEquals(
            "java.lang.Class java.lang.Object.getClass()",
            nullCheckInstruction.asInvokeVirtual().getInvokedMethod().toSourceString());
      } else {
        assertTrue(nullCheckInstruction.isInvokeStatic());
        assertEquals(
            "java.lang.Object java.util.Objects.requireNonNull(java.lang.Object)",
            nullCheckInstruction.asInvokeStatic().getInvokedMethod().toSourceString());
      }
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      testThrowNPE(new Object());
      testThrowNull(new Object());

      try {
        testThrowNPE(null);
      } catch (NullPointerException e) {
        System.out.println("Caught NPE");
      }
      try {
        testThrowNull(null);
      } catch (NullPointerException e) {
        System.out.println("Caught NPE");
      }
    }

    static void testThrowNPE(Object x) {
      if (x == null) {
        throw new NullPointerException();
      }
    }

    static void testThrowNull(Object x) {
      if (x == null) {
        throw null;
      }
    }
  }
}
