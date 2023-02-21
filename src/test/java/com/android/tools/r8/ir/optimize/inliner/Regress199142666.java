// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Regress199142666 extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection params() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public Regress199142666(TestParameters parameters) {
    this.parameters = parameters;
  }

  private byte[] getVirtualAAsStaticA() throws IOException {
    return transformer(VirtualA.class).setClassDescriptor(descriptor(StaticA.class)).transform();
  }

  @Test
  public void testInliningWhenInvalidCaller() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(HasInvalidStaticCall.class)
        .addProgramClassFileData(getVirtualAAsStaticA())
        .addKeepMainRule(HasInvalidStaticCall.class)
        .addKeepMethodRules(StaticA.class, "void foo()")
        .setMinApi(parameters)
        .run(parameters.getRuntime(), HasInvalidStaticCall.class)
        // TODO(b/199142666): We should consider if we want to inline in this case (there are no
        //  verification errors)
        .assertSuccessWithOutputLines("foochanged")
        .inspect(
            inspector -> {
              ensureThisNumberOfCalls(inspector, HasInvalidStaticCall.class, 1);
              ensureThisNumberThrowICCE(inspector, HasInvalidStaticCall.class, 1);
            });
  }

  @Test
  public void testInliningWhenInvalidTarget() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TargetHasInvalidStaticCall.class, CallsStaticFoo.class)
        .addProgramClassFileData(getVirtualAAsStaticA())
        .addKeepMainRule(TargetHasInvalidStaticCall.class)
        .addKeepMethodRules(StaticA.class, "void foo()")
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TargetHasInvalidStaticCall.class)
        .assertSuccessWithEmptyOutput()
        .inspect(
            inspector -> {
              ensureThisNumberOfCalls(inspector, TargetHasInvalidStaticCall.class, 0);
              ensureThisNumberThrowICCE(inspector, TargetHasInvalidStaticCall.class, 2);
            });
  }

  private void ensureThisNumberOfCalls(CodeInspector inspector, Class<?> clazz, int fooCalls) {
    long count =
        inspector
            .clazz(clazz)
            .mainMethod()
            .streamInstructions()
            .filter(InstructionSubject::isInvoke)
            .filter(invoke -> invoke.getMethod().name.toString().equals("foo"))
            .count();
    assertEquals(fooCalls, count);
  }

  private void ensureThisNumberThrowICCE(CodeInspector inspector, Class<?> clazz, int expected) {
    IRCode code = inspector.clazz(clazz).mainMethod().buildIR();
    long count =
        code.streamInstructions()
            .filter(Instruction::isThrow)
            .filter(
                instruction ->
                    instruction
                        .getFirstOperand()
                        .isDefinedByInstructionSatisfying(
                            definition ->
                                definition.isNewInstance()
                                    && definition
                                        .asNewInstance()
                                        .getType()
                                        .getTypeName()
                                        .equals(IncompatibleClassChangeError.class.getTypeName())))
            .count();
    assertEquals(expected, count);
  }

  static class StaticA {
    public static void callFoo() {
      StaticA.foo();
    }

    public static void foo() {
      System.out.println("foo");
    }
  }

  static class VirtualA {
    public static void callFoo() {
      new VirtualA().foo();
    }

    public void foo() {
      System.out.println("foochanged");
    }
  }

  static class CallsStaticFoo {
    public static void callStaticFoo() {
      StaticA.foo();
    }
  }

  static class HasInvalidStaticCall {
    public static void main(String[] args) {
      StaticA.callFoo();
      try {
        StaticA.foo();
      } catch (IncompatibleClassChangeError e) {
        // Expected
        return;
      }
      throw new RuntimeException("Should have thrown ICCE");
    }
  }

  static class TargetHasInvalidStaticCall {
    public static void main(String[] args) {
      boolean didThrow = false;
      try {
        CallsStaticFoo.callStaticFoo();
      } catch (IncompatibleClassChangeError e) {
        didThrow = true;
      }
      if (!didThrow) {
        throw new RuntimeException("Should have thrown ICCE");
      }
      try {
        StaticA.foo();
      } catch (IncompatibleClassChangeError e) {
        return;
      }
      throw new RuntimeException("Should have thrown ICCE");
    }
  }
}
