// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.enums;

import static junit.framework.TestCase.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnumValuesLengthTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public EnumValuesLengthTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testValuesLengthRemoved() throws Exception {
    testForR8(parameters.getBackend())
        .addKeepMainRule(Main.class)
        .addInnerClasses(EnumValuesLengthTest.class)
        .noMinification()
        .setMinApi(parameters.getApiLevel())
        .addOptionsModification(
            opt -> {
              opt.enableEnumValueOptimization = true;
              // We need to keep the switch map to ensure kept switch maps have their
              // values array length rewritten.
              opt.enableEnumSwitchMapRemoval = false;
            })
        .compile()
        .inspect(this::assertValuesLengthRemoved)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("0", "2", "5", "a", "D", "c", "D");
  }

  @Test
  public void testValuesLengthSwitchMapRemoved() throws Exception {
    // Make sure SwitchMap can still be removed with valuesLength optimization.
    assertSwitchMapPresent();
    testForR8(parameters.getBackend())
        .addKeepMainRule(Main.class)
        .addInnerClasses(EnumValuesLengthTest.class)
        .noMinification()
        .setMinApi(parameters.getApiLevel())
        .addOptionsModification(
            opt -> {
              opt.enableEnumValueOptimization = true;
            })
        .compile()
        .inspect(this::assertSwitchMapRemoved)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("0", "2", "5", "a", "D", "c", "D");
  }

  private void assertSwitchMapPresent() throws IOException {
    Collection<Path> classFilesForInnerClasses =
        ToolHelper.getClassFilesForInnerClasses(
            Collections.singletonList(EnumValuesLengthTest.class));
    assertTrue(classFilesForInnerClasses.stream().anyMatch(p -> p.toString().endsWith("$1.class")));
  }

  private void assertSwitchMapRemoved(CodeInspector inspector) {
    assertTrue(inspector.allClasses().stream().noneMatch(c -> c.getOriginalName().endsWith("$1")));
  }

  private void assertValuesLengthRemoved(CodeInspector inspector) {
    for (FoundClassSubject clazz : inspector.allClasses()) {
      clazz.forAllMethods(this::assertValuesLengthRemoved);
    }
  }

  private void assertValuesLengthRemoved(FoundMethodSubject method) {
    assertTrue(method.streamInstructions().noneMatch(InstructionSubject::isArrayLength));
    assertTrue(
        method
            .streamInstructions()
            .noneMatch(
                instr ->
                    instr.isInvokeStatic() && instr.getMethod().name.toString().equals("values")));
  }

  public static class Main {

    @NeverClassInline
    enum E0 {}

    @NeverClassInline
    enum E2 {
      A,
      B
    }

    @NeverClassInline
    enum E5 {
      A,
      B,
      C,
      D,
      E
    }

    @NeverClassInline
    enum EUnusedValues {
      A,
      B,
      C
    }

    @NeverClassInline
    enum ESwitch {
      A,
      B,
      C,
      D
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void main(String[] args) {
      EUnusedValues.values();
      System.out.println(E0.values().length);
      System.out.println(E2.values().length);
      System.out.println(E5.values().length);
      System.out.println(switchOn(ESwitch.A));
      System.out.println(switchOn(ESwitch.B));
      System.out.println(switchOn(ESwitch.C));
      System.out.println(switchOn(ESwitch.D));
    }

    // SwitchMaps feature an array length on values, and some of them are not removed.
    @NeverInline
    static char switchOn(ESwitch e) {
      switch (e) {
        case A:
          return 'a';
        case C:
          return 'c';
        default:
          return 'D';
      }
    }
  }
}
