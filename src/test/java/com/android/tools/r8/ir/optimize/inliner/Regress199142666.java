// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
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

  private byte[] getStaticAAsVirtualA() throws IOException {
    return transformer(StaticA.class).setClassDescriptor(descriptor(VirtualA.class)).transform();
  }

  @Test
  public void testInliningWhenInvalidTarget() throws Exception {
    R8TestRunResult run =
        testForR8(parameters.getBackend())
            .addProgramClasses(B.class)
            .addProgramClassFileData(getVirtualAAsStaticA())
            .addKeepMainRule(B.class)
            .addKeepMethodRules(StaticA.class, "void foo()")
            .setMinApi(parameters.getApiLevel())
            .run(parameters.getRuntime(), B.class);
    if (parameters.getRuntime().asDex().getVm().getVersion().isDalvik()) {
      // TODO(b/199142666): We should not inline to provoke this error.
      run.assertFailureWithErrorThatMatches(
          containsString("invoke type does not match method type of"));
    } else {
      run.assertSuccessWithOutputLines("foochanged")
          .inspect(inspector -> ensureThisNumberOfCalls(inspector, B.class));
    }
  }

  @Test
  public void testInliningWhenInvalidCaller() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(C.class)
        .addProgramClassFileData(getStaticAAsVirtualA())
        .addKeepMainRule(C.class)
        .addKeepMethodRules(VirtualA.class, "static void foo()")
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), C.class)
        .assertSuccessWithOutputLines("foo")
        .inspect(inspector -> ensureThisNumberOfCalls(inspector, C.class));
  }

  private void ensureThisNumberOfCalls(CodeInspector inspector, Class clazz) {
    long count =
        inspector
            .clazz(clazz)
            .mainMethod()
            .streamInstructions()
            .filter(InstructionSubject::isInvoke)
            .filter(invoke -> invoke.getMethod().name.toString().equals("foo"))
            .count();
    // TODO(b/199142666): We should not inline, so count should be 1.
    assertEquals(2, count);
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

  static class B {
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

  static class C {
    public static void main(String[] args) {
      VirtualA.callFoo();
      try {
        new VirtualA().foo();
      } catch (IncompatibleClassChangeError e) {
        return;
      }
      throw new RuntimeException("Should have thrown ICCE");
    }
  }
}
