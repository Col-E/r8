// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.uninstantiatedtypes;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SynchronizedMethodTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public SynchronizedMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("In A.m()", "Got NullPointerException");
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addInnerClasses(SynchronizedMethodTest.class)
            .addKeepMainRule(TestClass.class)
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .setMinApi(parameters.getApiLevel())
            .run(parameters.getRuntime(), TestClass.class)
            .assertSuccessWithOutput(expectedOutput)
            .inspector();

    ClassSubject clazz = inspector.clazz(A.class);
    MethodSubject method = clazz.uniqueMethodWithOriginalName("m");
    assertThat(method, isPresent());

    // The invoke on the uninstantiated turns into a "throw null", and the synchronized method
    // already have a throw instruction in the catch all handler ensuring monitor exit is called.
    List<InstructionSubject> throwInstructions =
        method
            .streamInstructions()
            .filter(InstructionSubject::isThrow)
            .collect(Collectors.toList());
    assertEquals(1 + BooleanUtils.intValue(parameters.isDexRuntime()), throwInstructions.size());

    if (parameters.isDexRuntime()) {
      // The inserted "throw null" should still be covered by the catch all to ensure monitor exit
      // is called.
      List<InstructionSubject> catchAllCoveredInstructions = new ArrayList<>();
      method
          .iterateTryCatches()
          .forEachRemaining(
              tryCatchSubject -> {
                if (tryCatchSubject.hasCatchAll()) {
                  catchAllCoveredInstructions.addAll(
                      throwInstructions.stream()
                          .filter(
                              throwInstruction ->
                                  tryCatchSubject
                                      .getRange()
                                      .includes(throwInstruction.getOffset(method)))
                          .collect(Collectors.toList()));
                }
              });
      assertEquals(1, catchAllCoveredInstructions.size());
      assertSame(throwInstructions.get(0), catchAllCoveredInstructions.get(0));
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      try {
        test(new A());
      } catch (NullPointerException e) {
        System.out.println("Got NullPointerException");
      }
    }

    @NeverInline
    private static void test(A obj) {
      obj.m();
    }
  }

  @NeverClassInline
  static class A {

    @NeverInline
    static Uninstantiated createUninstantiated() {
      return null;
    }

    @NeverInline
    public synchronized void m() {
      System.out.println("In A.m()");
      A.createUninstantiated().m();
    }
  }

  static class Uninstantiated {
    public void m() {
      System.out.println("In Uninstantiated.m()");
    }
  }
}
