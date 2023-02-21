// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LargeEnumUnboxingTest extends EnumUnboxingTestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "B1falsetruefalse1",
          "B2falsetruefalse2",
          "B3falsetruefalse3",
          "B4falsetruefalse4",
          "B5falsetruefalse5",
          "B6falsetruefalse6",
          "B7falsetruefalse7",
          "B8falsetruefalse8",
          "B9falsetruefalse9",
          "B10falsetruefalse10",
          "B11falsetruefalse11",
          "B12falsetruefalse12",
          "B13falsetruefalse13",
          "E1falsefalsetrue14",
          "E2falsefalsetrue15",
          "E3falsefalsetrue16",
          "E4falsefalsetrue17",
          "E5falsefalsetrue18",
          "E6falsefalsetrue19",
          "E7falsefalsetrue20",
          "E8falsefalsetrue21",
          "E9falsefalsetrue22",
          "E10falsefalsetrue23",
          "E11falsefalsetrue24",
          "E12falsefalsetrue25",
          "E13falsefalsetrue26",
          "E14falsefalsetrue27",
          "E15falsefalsetrue28",
          "G1falsefalsefalse29",
          "G2falsefalsefalse30",
          "G3falsefalsefalse31",
          "G4falsefalsefalse32",
          "G5falsefalsefalse33",
          "G6falsefalsefalse34",
          "G7falsefalsefalse35",
          "G8falsefalsefalse36",
          "G9falsefalsefalse37",
          "I1truefalsefalse38",
          "I2truefalsefalse39",
          "I3truefalsefalse40",
          "I4truefalsefalse41",
          "I5truefalsefalse42",
          "I6truefalsefalse43",
          "I7truefalsefalse44",
          "I8truefalsefalse45",
          "I9truefalsefalse46",
          "I10truefalsefalse47",
          "I11truefalsefalse48",
          "I12truefalsefalse49",
          "I13truefalsefalse50",
          "I14truefalsefalse51",
          "I15truefalsefalse52",
          "I16truefalsefalse53",
          "J1falsefalsefalse54",
          "J2falsefalsefalse55");

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public LargeEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    Class<?> mainClass = Main.class;
    testForR8(parameters.getBackend())
        .addProgramClasses(mainClass, LargeEnum.class)
        .addKeepMainRule(mainClass)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(LargeEnum.class))
        .enableNeverClassInliningAnnotations()
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), mainClass)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @NeverClassInline
  enum LargeEnum {
    B1("1"),
    B2("2"),
    B3("3"),
    B4("4"),
    B5("5"),
    B6("6"),
    B7("7"),
    B8("8"),
    B9("9"),
    B10("10"),
    B11("11"),
    B12("12"),
    B13("13"),

    E1("14"),
    E2("15"),
    E3("16"),
    E4("17"),
    E5("18"),
    E6("19"),
    E7("20"),
    E8("21"),
    E9("22"),
    E10("23"),
    E11("24"),
    E12("25"),
    E13("26"),
    E14("27"),
    E15("28"),

    G1("29"),
    G2("30"),
    G3("31"),
    G4("32"),
    G5("33"),
    G6("34"),
    G7("35"),
    G8("36"),
    G9("37"),

    I1("38"),
    I2("39"),
    I3("40"),
    I4("41"),
    I5("42"),
    I6("43"),
    I7("44"),
    I8("45"),
    I9("46"),
    I10("47"),
    I11("48"),
    I12("49"),
    I13("50"),
    I14("51"),
    I15("52"),
    I16("53"),

    J1("54"),
    J2("55");

    private final String num;

    LargeEnum(String num) {
      this.num = num;
    }

    public String getNum() {
      return num;
    }

    public boolean isI() {
      return this == I1
          || this == I2
          || this == I3
          || this == I4
          || this == I5
          || this == I6
          || this == I7
          || this == I8
          || this == I9
          || this == I10
          || this == I11
          || this == I12
          || this == I13
          || this == I14
          || this == I15
          || this == I16;
    }

    public boolean isB() {
      return this == B1
          || this == B2
          || this == B3
          || this == B4
          || this == B5
          || this == B6
          || this == B7
          || this == B8
          || this == B9
          || this == B10
          || this == B11
          || this == B12
          || this == B13;
    }

    public boolean isE() {
      return this == E1
          || this == E2
          || this == E3
          || this == E4
          || this == E5
          || this == E6
          || this == E7
          || this == E8
          || this == E9
          || this == E10
          || this == E11
          || this == E12
          || this == E13
          || this == E14
          || this == E15;
    }
  }

  static class Main {

    public static void main(String[] args) {
      for (LargeEnum value : LargeEnum.values()) {
        System.out.println(
            value.toString() + value.isI() + value.isB() + value.isE() + value.getNum());
      }
    }
  }
}
