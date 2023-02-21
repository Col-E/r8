// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b165825758;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverReprocessMethod;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionOffsetSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.codeinspector.RangeSubject;
import com.android.tools.r8.utils.codeinspector.TryCatchSubject;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Regress165825758Test extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public Regress165825758Test(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(Regress165825758Test.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .enableInliningAnnotations()
        .addInnerClasses(Regress165825758Test.class)
        .addKeepMainRule(TestClass.class)
        .addKeepClassRules(A.class)
        .enableNeverReprocessMethodAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkDeadCodeThrowInTryRange);
  }

  private void checkDeadCodeThrowInTryRange(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(A.class);
    assertThat(classSubject, isPresent());
    MethodSubject method = classSubject.uniqueMethodWithOriginalName("synchronizedMethod");
    assertThat(method, isPresent());

    // Ensure that the "throwNpe" method remains and that it was not inlined by checking that no
    // allocations of NullPointerException are in the method.
    assertTrue(method.streamInstructions().noneMatch(InstructionSubject::isNewInstance));
    assertThat(
        inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("throwNpe"), isPresent());

    // Source has 2 catch ranges:
    // 1st try catch is the source range, 2nd is the compiler inserted catch over monitor-exit.
    // When compiled with R8 the catch ranges are collapsed.
    List<TryCatchSubject> tryCatchSubjects = method.streamTryCatches().collect(Collectors.toList());
    assertEquals(1, tryCatchSubjects.size());
    TryCatchSubject sourceTry = tryCatchSubjects.get(0);

    // 1st throw is the "dead code" throw, the 2nd is the exceptional rethrow after method exit.
    List<InstructionSubject> throwInstructions =
        method
            .streamInstructions()
            .filter(InstructionSubject::isThrow)
            .collect(Collectors.toList());
    assertEquals(2, throwInstructions.size());
    InstructionSubject deadCodeThrow = throwInstructions.get(0);
    InstructionOffsetSubject throwOffset = deadCodeThrow.getOffset(method);
    RangeSubject range = sourceTry.getRange();
    assertTrue(
        "Expected throw@" + throwOffset + " to be in try-range " + range,
        range.includes(throwOffset));
  }

  static class A {

    @NeverInline
    @NeverReprocessMethod
    void synchronizedMethod() {
      synchronized (this) {
        TestClass.throwNpe();
        System.out.println("Never hit");
      }
    }
  }

  static class TestClass {

    @NeverInline
    static void throwNpe() {
      throw new NullPointerException();
    }

    public static void main(String[] args) {
      try {
        new A().synchronizedMethod();
      } catch (NullPointerException e) {
        System.out.println("Hello, world");
      }
    }
  }
}
