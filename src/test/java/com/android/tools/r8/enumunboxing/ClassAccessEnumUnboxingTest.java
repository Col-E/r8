// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ClassAccessEnumUnboxingTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public ClassAccessEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    Assume.assumeTrue("studio rules required to use valueOf", enumKeepRules.isStudio());
    testForR8(parameters.getBackend())
        .addInnerClasses(ClassAccessEnumUnboxingTest.class)
        .addKeepMainRule(Main.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addEnumUnboxingInspector(
            inspector ->
                inspector
                    .assertUnboxed(ProtoEnumLike.class, UnboxableEnum.class)
                    .assertNotUnboxed(EscapingEnum1.class, EscapingEnum2.class))
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccess()
        .inspectStdOut(this::assertLines2By2Correct);
  }

  @NeverClassInline
  enum ProtoEnumLike {
    A,
    B,
    C;

    @Override
    @NeverInline
    public String toString() {
      return "an instance of " + this.getClass().getName();
    }
  }

  @NeverClassInline
  enum UnboxableEnum {
    A,
    B,
    C;

    @Override
    @NeverInline
    public String toString() {
      return "an instance of "
          + this.getClass().getName()
          + this.getClass().getSimpleName()
          + this.getClass().getCanonicalName();
    }
  }

  @NeverClassInline
  enum EscapingEnum1 {
    A,
    B,
    C;
  }

  @NeverClassInline
  enum EscapingEnum2 {
    A,
    B,
    C;
  }

  static class Main {

    public static void main(String[] args) {
      unboxableProtoCase();
      unboxableCase();
      nonUnboxableCase();
    }

    @NeverInline
    private static void unboxableCase() {
      System.out.println(UnboxableEnum.A.ordinal());
      System.out.println(0);
      System.out.println(UnboxableEnum.A.toString());
      System.out.println("an instance of intintint");
    }

    @NeverInline
    private static void unboxableProtoCase() {
      System.out.println(ProtoEnumLike.A.ordinal());
      System.out.println(0);
      System.out.println(ProtoEnumLike.A.toString());
      System.out.println("an instance of int");
    }

    @NeverInline
    private static void nonUnboxableCase() {
      System.out.println(EscapingEnum1.A.ordinal());
      System.out.println(0);
      System.out.println(EscapingEnum2.A.ordinal());
      System.out.println(0);
      System.out.println(EscapingEnum1.B.ordinal());
      System.out.println(1);
      System.out.println(EscapingEnum2.B.ordinal());
      System.out.println(1);
      System.out.println(getEnum(EscapingEnum1.class, "A").ordinal());
      System.out.println(0);
      System.out.println(getEnum(EscapingEnum2.class, "A").ordinal());
      System.out.println(0);
      System.out.println(getEnum(EscapingEnum1.class, "B").ordinal());
      System.out.println(1);
      System.out.println(getEnum(EscapingEnum2.class, "B").ordinal());
      System.out.println(1);
    }

    @NeverInline
    private static <T extends Enum<T>> Enum<T> getEnum(Class<T> enumClass, String value) {
      return Enum.valueOf(enumClass, value);
    }
  }
}
