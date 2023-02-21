// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SwitchEnumUnboxingTest extends EnumUnboxingTestBase {

  private static final Class<MyEnumFewCases> ENUM_CLASS = MyEnumFewCases.class;

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public SwitchEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    Class<Switch> classToTest = Switch.class;
    testForR8(parameters.getBackend())
        .addInnerClasses(SwitchEnumUnboxingTest.class)
        .addKeepMainRule(classToTest)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(ENUM_CLASS))
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .addDontObfuscate() // For assertions.
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .setMinApi(parameters)
        .compile()
        .inspect(this::assertSwitchPresentButSwitchMapRemoved)
        .run(parameters.getRuntime(), classToTest)
        .assertSuccess()
        .inspectStdOut(this::assertLines2By2Correct);
  }

  private void assertSwitchPresentButSwitchMapRemoved(CodeInspector i) {
    if (enumValueOptimization) {
      assertFalse(
          i.clazz("com.android.tools.r8.enumunboxing.SwitchEnumUnboxingTest$1").isPresent());
    }
    assertTrue(
        i.clazz(Switch.class)
            .uniqueMethodWithOriginalName("switchOnEnumManyCases")
            .streamInstructions()
            .anyMatch(InstructionSubject::isSwitch));
  }

  @NeverClassInline
  enum MyEnumFewCases {
    A,
    B,
    C;

    @NeverInline
    void print() {
      Switch.packagePrivatePrint();
    }
  }

  @NeverClassInline
  enum MyEnumManyCases {
    A,
    B,
    C,
    D,
    E,
    F,
    G,
    H,
    I;

    @NeverInline
    void print() {
      Switch.packagePrivatePrint();
    }
  }

  static class Switch {

    public static void main(String[] args) {
      System.out.println(switchOnEnumFewCases(MyEnumFewCases.A));
      System.out.println(0xC0FFEE);
      System.out.println(switchOnEnumFewCases(MyEnumFewCases.B));
      System.out.println(0xBABE);

      System.out.println(switchOnEnumManyCases(MyEnumManyCases.A));
      System.out.println(0xACE);
      System.out.println(switchOnEnumManyCases(MyEnumManyCases.B));
      System.out.println(0xBABE);
      System.out.println(switchOnEnumManyCases(MyEnumManyCases.C));
      System.out.println(0xC0FFEE);
      System.out.println(switchOnEnumManyCases(MyEnumManyCases.D));
      System.out.println(0xDEC0DE);
      System.out.println(switchOnEnumManyCases(MyEnumManyCases.E));
      System.out.println(0xEFFACE);
      System.out.println(switchOnEnumManyCases(MyEnumManyCases.F));
      System.out.println(0xF00D);
      System.out.println(switchOnEnumManyCases(MyEnumManyCases.G));
      System.out.println(0x0);
      System.out.println(switchOnEnumManyCases(MyEnumManyCases.H));
      System.out.println(0x1);
      System.out.println(switchOnEnumManyCases(MyEnumManyCases.I));
      System.out.println(0x2);

      MyEnumFewCases.A.print();
      MyEnumManyCases.A.print();
    }

    @NeverInline
    static void packagePrivatePrint() {
      System.out.println("package private dependency");
    }

    // This switch will be converted into branches.
    @NeverInline
    static int switchOnEnumFewCases(MyEnumFewCases e) {
      switch (e) {
        case A:
          return 0xC0FFEE;
        case B:
          return 0xBABE;
        default:
          return 0xDEADBEEF;
      }
    }

    // This switch will remain a switch.
    @NeverInline
    static int switchOnEnumManyCases(MyEnumManyCases e) {
      switch (e) {
        case A:
          return 0xACE;
        case B:
          return 0xBABE;
        case C:
          return 0xC0FFEE;
        case D:
          return 0xDEC0DE;
        case E:
          return 0xEFFACE;
        case F:
          return 0xF00D;
        case G:
          return 0x0;
        case H:
          return 0x1;
        case I:
          return 0x2;
        default:
          return 0xDEADBEEF;
      }
    }
  }
}
