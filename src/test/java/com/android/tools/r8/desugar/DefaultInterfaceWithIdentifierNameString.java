// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.InvokeInstructionSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DefaultInterfaceWithIdentifierNameString extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    // Test expect that default interface method desugaring is enabled.
    return getTestParameters()
        .withDexRuntimes()
        .withApiLevelsEndingAtExcluding(AndroidApiLevel.N)
        .build();
  }

  public DefaultInterfaceWithIdentifierNameString(TestParameters parameters) {
    this.parameters = parameters;
  }

  private boolean isInvokeClassForName(InstructionSubject instruction) {
    if (!instruction.isInvokeStatic()) {
      return false;
    }
    return ((InvokeInstructionSubject) instruction)
        .invokedMethod()
        .name
        .toString()
        .equals("forName");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(A.class);
    assertThat(classSubject, isPresentAndRenamed());
    ClassSubject companionClassSubject = inspector.companionClassFor(I.class);
    assertThat(companionClassSubject, isPresentAndRenamed());
    companionClassSubject
        .allMethods()
        .forEach(
            method ->
                assertTrue(method.streamInstructions().noneMatch(this::isInvokeClassForName)));
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(DefaultInterfaceWithIdentifierNameString.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters.getApiLevel())
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("new A", "new A", "DONE");
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      I.newInstance().antoherInstance();
      System.out.println("DONE");
    }
  }

  @NoVerticalClassMerging
  interface I {
    @NeverInline
    static I newInstance() throws Exception {
      return (I)
          Class.forName("com.android.tools.r8.desugar.DefaultInterfaceWithIdentifierNameString$A")
              .newInstance();
    }

    @NeverInline
    default I antoherInstance() throws Exception {
      return (I)
          Class.forName("com.android.tools.r8.desugar.DefaultInterfaceWithIdentifierNameString$A")
              .newInstance();
    }
  }

  @NeverClassInline
  static class A implements I {
    A() {
      System.out.println("new A");
    }
  }
}
