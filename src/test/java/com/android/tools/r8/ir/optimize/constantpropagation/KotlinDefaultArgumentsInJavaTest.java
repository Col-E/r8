// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.constantpropagation;

import static com.android.tools.r8.ir.optimize.constantpropagation.KotlinDefaultArgumentsInJavaTest.Greeter.getHello;
import static com.android.tools.r8.ir.optimize.constantpropagation.KotlinDefaultArgumentsInJavaTest.Greeter.getWorld;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.Sets;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KotlinDefaultArgumentsInJavaTest extends TestBase {

  enum Effect {
    MATERIALIZED,
    NONE,
    REMOVED
  }

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testFirstBranchRemoved() throws Exception {
    test(MainRemovedNone.class, Effect.REMOVED, Effect.NONE);
  }

  @Test
  public void testBothBranchesRemoved() throws Exception {
    test(MainRemovedRemoved.class, Effect.REMOVED, Effect.REMOVED);
  }

  @Test
  public void testFirstBranchRemovedAndSecondBranchMaterialized() throws Exception {
    test(MainRemovedMaterialized.class, Effect.REMOVED, Effect.MATERIALIZED);
  }

  @Test
  public void testFirstBranchMaterialized() throws Exception {
    test(MainMaterializedNone.class, Effect.MATERIALIZED, Effect.NONE);
  }

  @Test
  public void testFirstBranchMaterializedAndSecondBranchRemoved() throws Exception {
    test(MainMaterializedRemoved.class, Effect.MATERIALIZED, Effect.REMOVED);
  }

  @Test
  public void testBothBranchesMaterialized() throws Exception {
    test(MainMaterializedMaterialized.class, Effect.MATERIALIZED, Effect.MATERIALIZED);
  }

  private void test(Class<?> mainClass, Effect firstBranchEffect, Effect secondBranchEffect)
      throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(mainClass, Greeter.class)
        .addKeepMainRule(mainClass)
        .addKeepRules(
            "-keepclassmembers class " + Greeter.class.getTypeName() + " {",
            "  static void label*();",
            "}")
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(inspector -> inspect(inspector, firstBranchEffect, secondBranchEffect))
        .run(parameters.getRuntime(), mainClass)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  private void inspect(
      CodeInspector inspector, Effect firstBranchEffect, Effect secondBranchEffect) {
    ClassSubject greeterClassSubject = inspector.clazz(Greeter.class);
    assertThat(greeterClassSubject, isPresent());

    MethodSubject greetMethodSubject = greeterClassSubject.uniqueMethodWithOriginalName("greet");
    assertThat(greetMethodSubject, isPresent());

    // Build IR and find the three label instructions.
    IRCode code = greetMethodSubject.buildIR();

    Instruction labelInitializeX =
        code.instructionListIterator()
            .nextUntil(
                instruction ->
                    instruction.isInvokeStatic()
                        && instruction
                            .asInvokeStatic()
                            .getInvokedMethod()
                            .getName()
                            .isEqualTo("labelInitializeX"));
    assertNotNull(labelInitializeX);

    Instruction labelInitializeY =
        code.instructionListIterator()
            .nextUntil(
                instruction ->
                    instruction.isInvokeStatic()
                        && instruction
                            .asInvokeStatic()
                            .getInvokedMethod()
                            .getName()
                            .isEqualTo("labelInitializeY"));
    assertNotNull(labelInitializeY);

    Instruction labelFullyInitialized =
        code.instructionListIterator()
            .nextUntil(
                instruction ->
                    instruction.isInvokeStatic()
                        && instruction
                            .asInvokeStatic()
                            .getInvokedMethod()
                            .getName()
                            .isEqualTo("labelFullyInitialized"));
    assertNotNull(labelFullyInitialized);

    Set<Instruction> instructionsReachableFromLabelInitializeX =
        SetUtils.newIdentityHashSet(code.getInstructionsReachableFrom(labelInitializeX));
    Set<Instruction> instructionsReachableFromLabelInitializeY =
        SetUtils.newIdentityHashSet(code.getInstructionsReachableFrom(labelInitializeY));
    Set<Instruction> instructionsReachableFromLabelFullyInitialized =
        SetUtils.newIdentityHashSet(code.getInstructionsReachableFrom(labelFullyInitialized));

    // Inspect the code.
    int numberOfExpectedIfInstructions =
        BooleanUtils.intValue(firstBranchEffect == Effect.NONE)
            + BooleanUtils.intValue(secondBranchEffect == Effect.NONE);
    assertEquals(
        numberOfExpectedIfInstructions,
        code.streamInstructions().filter(Instruction::isIf).count());

    Set<Instruction> firstBranchInstructions =
        Sets.difference(
            instructionsReachableFromLabelInitializeX, instructionsReachableFromLabelInitializeY);
    inspectBranchInstructions(code, firstBranchInstructions, firstBranchEffect, "Hello");

    Set<Instruction> secondBranchInstructions =
        Sets.difference(
            instructionsReachableFromLabelInitializeY,
            instructionsReachableFromLabelFullyInitialized);
    inspectBranchInstructions(code, secondBranchInstructions, secondBranchEffect, ", world!");

    // Inspect that the parameters were removed.
    int numberOfExpectedParameters =
        BooleanUtils.intValue(firstBranchEffect != Effect.MATERIALIZED)
            + BooleanUtils.intValue(secondBranchEffect != Effect.MATERIALIZED)
            + BooleanUtils.intValue(
                firstBranchEffect == Effect.NONE || secondBranchEffect == Effect.NONE);
    assertEquals(numberOfExpectedParameters, greetMethodSubject.getParameters().size());
  }

  private void inspectBranchInstructions(
      IRCode code, Set<Instruction> branchInstructions, Effect branchEffect, String defaultValue) {
    switch (branchEffect) {
      case MATERIALIZED:
        // The if-instruction should be removed and the default value for the parameter should be
        // present.
        assertTrue(branchInstructions.stream().noneMatch(Instruction::isIf));
        assertTrue(
            code.streamInstructions()
                .anyMatch(
                    instruction ->
                        instruction.isConstString()
                            && instruction.asConstString().getValue().isEqualTo(defaultValue)));
        break;
      case NONE:
        // The if-instruction should be present along with the default value for the parameter.
        assertTrue(branchInstructions.stream().anyMatch(Instruction::isIf));
        assertTrue(
            branchInstructions.stream()
                .anyMatch(
                    instruction ->
                        instruction.isConstString()
                            && instruction.asConstString().getValue().isEqualTo(defaultValue)));
        break;
      case REMOVED:
        // The if-instruction and the default value for the parameter should be removed.
        assertTrue(branchInstructions.stream().noneMatch(Instruction::isIf));
        assertTrue(
            code.streamInstructions()
                .noneMatch(
                    instruction ->
                        instruction.isConstString()
                            && instruction.asConstString().getValue().isEqualTo(defaultValue)));
        break;
      default:
        throw new Unreachable();
    }
  }

  // Test where first parameter X is always given at the call site.
  // The first branch in greet() should be removed.
  static class MainRemovedNone {

    public static void main(String[] args) {
      Greeter.greet(getHello(), null, 2);
      if (System.currentTimeMillis() < 0) {
        Greeter.greet(getHello(), getWorld(), 0);
      }
    }
  }

  // Test where both parameters X and Y are always given at the call site.
  // Both branches in greet() should be removed.
  static class MainRemovedRemoved {

    public static void main(String[] args) {
      Greeter.greet(getHello(), getWorld(), 0);
    }
  }

  // Test where the first parameter X is always given and the second parameter Y is never given at
  // the call site.
  // The first branch in greet() should be removed and the second branch in greet() should be
  // "materialized".
  static class MainRemovedMaterialized {

    public static void main(String[] args) {
      Greeter.greet(getHello(), null, 2);
    }
  }

  // Test where the first parameter X is never given at the call site.
  // The first branch in greet() should be materialized.
  static class MainMaterializedNone {

    public static void main(String[] args) {
      Greeter.greet(null, null, 3);
      if (System.currentTimeMillis() < 0) {
        Greeter.greet(null, getWorld(), 1);
      }
    }
  }

  // Test where the first parameter X is never given and the second parameter Y is always given at
  // the call site.
  // The first branch in greet() should be materialized and the second branch should be removed.
  static class MainMaterializedRemoved {

    public static void main(String[] args) {
      Greeter.greet(null, getWorld(), 1);
    }
  }

  // Test where none of the parameters X and Y are given at the call site.
  // Both branches in greet() should be materialized.
  static class MainMaterializedMaterialized {

    public static void main(String[] args) {
      Greeter.greet(null, null, 3);
    }
  }

  static class Greeter {

    @NeverInline
    static void greet(String x, String y, int defaults) {
      labelInitializeX();
      if ((defaults & 1) != 0) {
        x = "Hello";
      }
      labelInitializeY();
      if ((defaults & 2) != 0) {
        y = ", world!";
      }
      labelFullyInitialized();
      System.out.print(x);
      System.out.println(y);
    }

    @NeverInline
    static String getHello() {
      return System.currentTimeMillis() > 0 ? "Hello" : "";
    }

    @NeverInline
    static String getWorld() {
      return System.currentTimeMillis() > 0 ? ", world!" : "";
    }

    // @Keep
    static void labelInitializeX() {}

    // @Keep
    static void labelInitializeY() {}

    // @Keep
    static void labelFullyInitialized() {}
  }
}
