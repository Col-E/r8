// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.movestringconstants;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.AlwaysInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject.JumboStringMode;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.Iterator;
import java.util.function.Predicate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MoveStringConstantsTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepClassAndMembersRules(TestClass.class)
        .addDontObfuscate()
        .allowAccessModification()
        .enableAlwaysInliningAnnotations()
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithEmptyOutput();
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(TestClass.class);
    assertTrue(clazz.isPresent());

    // Check the instruction used for testInlinedIntoVoidMethod
    MethodSubject methodThrowToBeInlined =
        clazz.method("void", "foo", ImmutableList.of(
            "java.lang.String", "java.lang.String", "java.lang.String", "java.lang.String"));
    assertTrue(methodThrowToBeInlined.isPresent());

    // CF should not canonicalize strings or lower them. This test ensures that strings are moved
    // down and make assertions based on throwing branches, which we do not care about in CF.
    // See (r8g/30163) and (r8g/30320).
    if (parameters.isDexRuntime()) {
      validateSequence(
          methodThrowToBeInlined.iterateInstructions(),

          // 'if' with "foo#1" is flipped.
          InstructionSubject::isIfEqz,

          // 'if' with "foo#2" is removed along with the constant.

          // 'if' with "foo#3" is removed so now we have an unconditional call inside the branch.
          InstructionSubject::isIfEq,

          // 'if' with "foo#4" is flipped, but the throwing branch is not moved to the end of the
          // code
          // (area for improvement?).
          insn -> insn.isConstString("StringConstants::foo#4", JumboStringMode.DISALLOW),
          InstructionSubject::isIfEqz, // Flipped if
          InstructionSubject::isGoto, // Jump around throwing branch.
          InstructionSubject::isInvokeStatic, // Throwing branch.
          InstructionSubject::isThrow,

          // 'if's with "foo#5" are flipped.
          insn -> insn.isConstString("StringConstants::foo#5", JumboStringMode.DISALLOW),
          InstructionSubject::isIfEqz, // Flipped if
          InstructionSubject::isReturnVoid, // Final return statement.
          InstructionSubject::isInvokeStatic, // Throwing branch.
          InstructionSubject::isThrow,

          // 'if' with "foo#3" is removed so now we have an unconditional call.
          insn -> insn.isConstString("StringConstants::foo#3", JumboStringMode.DISALLOW),
          InstructionSubject::isInvokeStatic,
          InstructionSubject::isThrow,

          // After 'if' with "foo#1" flipped, the always throwing branch is moved here along with
          // the
          // constant.
          insn -> insn.isConstString("StringConstants::foo#1", JumboStringMode.DISALLOW),
          InstructionSubject::isInvokeStatic,
          InstructionSubject::isThrow);

      ClassSubject utilsClassSubject = inspector.clazz(Utils.class);
      assertThat(utilsClassSubject, isPresent());
      assertThat(utilsClassSubject.uniqueMethodWithOriginalName("throwException"), isPresent());
      assertEquals(1, utilsClassSubject.allMethods().size());
    }
  }

  @SafeVarargs
  private final void validateSequence(
      Iterator<InstructionSubject> instructions, Predicate<InstructionSubject>... checks) {
    int index = 0;

    while (instructions.hasNext()) {
      if (index >= checks.length) {
        return;
      }
      if (checks[index].test(instructions.next())) {
        index++;
      }
    }

    assertTrue("Not all checks processed", index >= checks.length);
  }

  public static class TestClass {
    public static void main(String[] args) {}

    static void foo(String arg1, String arg2, String arg3, String arg4) {
      Utils.check(arg1, "StringConstants::foo#1");
      Utils.check("", "StringConstants::foo#2");
      if (arg2.length() == 12345) {
        Utils.check(null, "StringConstants::foo#3");
      }
      try {
        Utils.check(arg3, "StringConstants::foo#4");
      } catch (Exception e) {
        System.out.println(e.getMessage());
      }
      try {
        Utils.check(arg4, "StringConstants::foo#5");
      } finally {
        System.out.println("finally");
      }
    }
  }

  static class Utils {
    @AlwaysInline
    static void check(Object value, String message) {
      if (value == null) {
        throwException(message);
      }
    }

    @NeverInline
    private static void throwException(String message) {
      throw new RuntimeException(message);
    }
  }
}
