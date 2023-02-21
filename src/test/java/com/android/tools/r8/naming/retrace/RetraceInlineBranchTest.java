// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.retrace;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetraceInlineBranchTest extends TestBase {

  @Parameter() public TestParameters parameters;

  public StackTrace expectedStackTrace;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Before
  public void setup() throws Exception {
    // Get the expected stack trace by running on the JVM.
    expectedStackTrace =
        testForRuntime(parameters)
            .addInnerClasses(getClass())
            .run(parameters.getRuntime(), Main.class)
            .getStackTrace();
  }

  @Test
  public void testR8() throws Throwable {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .addKeepAttributeLineNumberTable()
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .inspectStackTrace(
            (stackTrace, inspector) -> {
              MethodSubject methodSubject =
                  inspector.clazz(Main.class).uniqueMethodWithOriginalName("call");
              assertThat(methodSubject, isPresent());
              List<InstructionSubject> getClassCalls =
                  methodSubject
                      .streamInstructions()
                      .filter(
                          instructionSubject ->
                              instructionSubject.isInvoke()
                                  && instructionSubject
                                      .getMethod()
                                      .qualifiedName()
                                      .contains("getClass"))
                      .collect(Collectors.toList());
              assertEquals(1, getClassCalls.size());
              Optional<InstructionSubject> firstIf =
                  methodSubject.streamInstructions().filter(InstructionSubject::isIf).findFirst();
              assertTrue(firstIf.isPresent());
              if (methodSubject.hasLineNumberTable()) {
                assertTrue(
                    methodSubject.getLineNumberForInstruction(getClassCalls.get(0))
                        > methodSubject.getLineNumberForInstruction(firstIf.get()));
              }
              assertThat(stackTrace, isSame(expectedStackTrace));
            });
  }

  @NeverClassInline
  static class Foo {

    private int value;

    @NeverInline
    int getValue() {
      return value;
    }

    @NeverInline
    void setValue(int value) {
      this.value = value;
    }

    void inlinable(int arg) {
      String newValue;
      if (arg > 0) {
        newValue = getValue() + "";
      } else {
        if (arg < 0) {
          setValue(arg);
          newValue = "-1";
        } else {
          // When inlining this block this is the only path that needs a null-check.
          newValue = "Hello World";
        }
      }
      System.out.println(newValue);
    }
  }

  static class Main {

    @NeverInline
    private static void call(Foo foo, String[] args) {
      foo.inlinable(args.length);
    }

    public static void main(String[] args) {
      call(args.length == 0 ? null : new Foo(), args);
    }
  }
}
