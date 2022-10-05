// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.Streams;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RedundantConstNumberRemovalTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimes()
        .withDexRuntimesStartingFromExcluding(Version.V4_4_4)
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.L)
        .build();
  }

  public RedundantConstNumberRemovalTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    String expectedOutput =
        StringUtils.lines(
            "true", "true", "true", "true", "true", "true", "true", "true", "true", "true", "true",
            "true", "true", "true", "true", "true");

    if (parameters.getBackend() == Backend.CF) {
      testForJvm().addTestClasspath().run(TestClass.class).assertSuccessWithOutput(expectedOutput);
    }

    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addInnerClasses(RedundantConstNumberRemovalTest.class)
            .addKeepClassAndMembersRules(TestClass.class)
            .enableInliningAnnotations()
            .addOptionsModification(
                internalOptions -> internalOptions.enableRedundantConstNumberOptimization = true)
            .setMinApi(parameters.getApiLevel())
            .run(TestClass.class)
            .assertSuccessWithOutput(expectedOutput);

    ClassSubject classSubject = result.inspector().clazz(TestClass.class);
    verifyBooleanCheckTest(classSubject.uniqueMethodWithOriginalName("booleanCheckTest"));
    verifyBooleanCheckTest(classSubject.uniqueMethodWithOriginalName("negateBooleanCheckTest"));
    verifyIntCheckTest(classSubject.uniqueMethodWithOriginalName("intCheckTest"));
    verifyIntCheckTest(classSubject.uniqueMethodWithOriginalName("negateIntCheckTest"));
    verifyNullCheckTest(classSubject.uniqueMethodWithOriginalName("nullCheckTest"));
    verifyNullCheckTest(classSubject.uniqueMethodWithOriginalName("invertedNullCheckTest"));
    verifyNullCheckTest(classSubject.uniqueMethodWithOriginalName("nonNullCheckTest"));
    verifyNullCheckWithWrongTypeTest(
        classSubject.uniqueMethodWithOriginalName("nullCheckWithWrongTypeTest"));
  }

  private void verifyBooleanCheckTest(MethodSubject methodSubject) {
    assertThat(methodSubject, isPresent());

    if (parameters.getBackend() == Backend.DEX) {
      // Check that the generated code for booleanCheckTest() only has a return instruction.
      assertEquals(1, methodSubject.streamInstructions().count());
      assertTrue(methodSubject.iterateInstructions().next().isReturn());
    } else {
      assert parameters.getBackend() == Backend.CF;
      // Check that the generated code for booleanCheckTest() only has return instructions that
      // return the argument.
      // TODO(christofferqa): CF backend does not share identical prefix of successors.
      // TODO(mkroghj): Redundant ConstNumber has also been disabled on CF, by
      //   canHaveDalvikIntUsedAsNonIntPrimitiveTypeBug() that checks for CF.
      // IRCode code = methodSubject.buildIR();
      // assertTrue(
      //     Streams.stream(code.instructionIterator())
      //         .filter(Instruction::isReturn)
      //         .allMatch(
      //             instruction -> instruction.asReturn().returnValue().definition.isArgument()));
    }
  }

  private void verifyIntCheckTest(MethodSubject methodSubject) {
    assertThat(methodSubject, isPresent());
    IRCode code = methodSubject.buildIR();

    if (parameters.getBackend() == Backend.DEX) {
      // Only a single basic block.
      assertEquals(1, code.blocks.size());
      // The block only has three instructions.
      BasicBlock entryBlock = code.entryBlock();
      assertEquals(2, entryBlock.getInstructions().size());
      // The first one is the `argument` instruction.
      Instruction argument = entryBlock.getInstructions().getFirst();
      assertTrue(argument.isArgument());
      // The `return` instruction returns the argument.
      Instruction ret = entryBlock.getInstructions().getLast();
      assertTrue(ret.isReturn());
      assertTrue(ret.asReturn().returnValue().definition.isArgument());
    } else {
      // Check that the generated code for intCheckTest() only has return instructions that
      // return the argument.
      // TODO(christofferqa): CF backend does not share identical prefix of successors.
      // TODO(mkroghj): Redundant ConstNumber has also been disabled on CF, by
      //   canHaveDalvikIntUsedAsNonIntPrimitiveTypeBug() that checks for CF.
      // assertTrue(
      //     Streams.stream(code.instructionIterator())
      //         .filter(Instruction::isReturn)
      //         .allMatch(
      //             instruction -> instruction.asReturn().returnValue().definition.isArgument()));
    }
  }

  private void verifyNullCheckTest(MethodSubject methodSubject) {
    // Check that the generated code for nullCheckTest() only has a single `const-null` instruction.
    assertThat(methodSubject, isPresent());
    assertEquals(
        1, methodSubject.streamInstructions().filter(InstructionSubject::isConstNull).count());

    // Also check that one of the return instructions actually returns the argument.
    IRCode code = methodSubject.buildIR();
    assertEquals(1, code.collectArguments().size());
    // TODO(b/120257211): D8 should replace `return null` by `return arg`.
    assertFalse(
        code.collectArguments().get(0).uniqueUsers().stream().anyMatch(Instruction::isReturn));
  }

  private void verifyNullCheckWithWrongTypeTest(MethodSubject methodSubject) {
    // Check that the generated code for nullCheckWithWrongTypeTest() still has a `return null`
    // instruction.
    assertThat(methodSubject, isPresent());
    IRCode code = methodSubject.buildIR();

    // Check that the code returns null.
    assertTrue(
        Streams.stream(code.instructionIterator())
            .anyMatch(
                instruction ->
                    instruction.isReturn()
                        && instruction.asReturn().returnValue().definition.isConstNumber()));

    // Also check that none of the return instructions actually returns the argument.
    assertEquals(1, code.collectArguments().size());
    assertTrue(
        code.collectArguments().get(0).uniqueUsers().stream().noneMatch(Instruction::isReturn));
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(booleanCheckTest(true) == true);
      System.out.println(booleanCheckTest(false) == false);
      System.out.println(negateBooleanCheckTest(true) == true);
      System.out.println(negateBooleanCheckTest(false) == false);
      System.out.println(intCheckTest(0) == 0);
      System.out.println(intCheckTest(42) == 42);
      System.out.println(negateIntCheckTest(0) == 0);
      System.out.println(negateIntCheckTest(42) == 42);
      System.out.println(nullCheckTest(new Object()) != null);
      System.out.println(nullCheckTest(null) == null);
      System.out.println(invertedNullCheckTest(new Object()) != null);
      System.out.println(invertedNullCheckTest(null) == null);
      System.out.println(nonNullCheckTest(new Object()) != null);
      System.out.println(nonNullCheckTest(null) == null);
      System.out.println(nullCheckWithWrongTypeTest(new Object()) != null);
      System.out.println(nullCheckWithWrongTypeTest(null) == null);
    }

    @NeverInline
    private static boolean booleanCheckTest(boolean x) {
      if (x) {
        return true; // should be replaced by `x`.
      }
      return false; // should be replaced by `x`.
    }

    @NeverInline
    private static boolean negateBooleanCheckTest(boolean x) {
      if (!x) {
        return false; // should be replaced by `x`
      }
      return true; // should be replaced by `x`
    }

    @NeverInline
    private static int intCheckTest(int x) {
      if (x == 42) {
        return 42; // should be replaced by `x`.
      }
      return x;
    }

    @NeverInline
    private static int negateIntCheckTest(int x) {
      if (x != 42) {
        return x;
      }
      return 42; // should be replaced by `x`
    }

    @NeverInline
    private static Object nullCheckTest(Object x) {
      if (x != null) {
        return new Object();
      }
      return null; // should be replaced by `x`.
    }

    @NeverInline
    private static Object invertedNullCheckTest(Object x) {
      if (null == x) {
        return null; // should be replaced by `x`.
      }
      return new Object();
    }

    @NeverInline
    private static Object nonNullCheckTest(Object x) {
      if (x == null) {
        return null; // should be replaced by `x`
      }
      return new Object();
    }

    @NeverInline
    private static Throwable nullCheckWithWrongTypeTest(Object x) {
      if (x != null) {
        return new Throwable();
      }
      return null; // cannot be replaced by `x` because Object is not a subtype of Throwable.
    }
  }
}
