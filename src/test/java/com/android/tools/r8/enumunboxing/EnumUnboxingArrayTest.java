// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnumUnboxingArrayTest extends EnumUnboxingTestBase {

  private static final Class<?>[] TESTS = {
    Enum2DimArrayReadWrite.class,
    EnumArrayNullRead.class,
    EnumArrayReadWrite.class,
    EnumArrayReadWriteNoEscape.class,
    EnumVarArgs.class,
  };

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public EnumUnboxingArrayTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    R8TestCompileResult compile =
        testForR8(parameters.getBackend())
            .addInnerClasses(EnumUnboxingArrayTest.class)
            .addKeepMainRules(TESTS)
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .addKeepRules(enumKeepRules.getKeepRules())
            .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
            .addEnumUnboxingInspector(
                inspector ->
                    inspector.assertUnboxed(
                        Enum2DimArrayReadWrite.MyEnum.class,
                        EnumArrayNullRead.MyEnum.class,
                        EnumArrayReadWrite.MyEnum.class,
                        EnumArrayReadWriteNoEscape.MyEnum.class,
                        EnumVarArgs.MyEnum.class))
            .setMinApi(parameters)
            .compile();
    for (Class<?> main : TESTS) {
      compile
          .run(parameters.getRuntime(), main)
          .assertSuccess()
          .inspectStdOut(this::assertLines2By2Correct);
    }
  }

  static class EnumVarArgs {

    public static void main(String[] args) {
      System.out.println(sum(MyEnum.A));
      System.out.println(1);
      System.out.println(sum(MyEnum.B, MyEnum.C));
      System.out.println(2);
    }

    @NeverInline
    public static int sum(MyEnum... args) {
      return args.length;
    }

    @NeverClassInline
    enum MyEnum {
      A,
      B,
      C;
    }
  }

  static class EnumArrayReadWriteNoEscape {

    public static void main(String[] args) {
      MyEnum[] myEnums = new MyEnum[2];
      myEnums[1] = MyEnum.C;
      System.out.println(myEnums[1].ordinal());
      System.out.println(2);
      System.out.println(myEnums[0] == null);
      System.out.println("true");
      myEnums[0] = MyEnum.B;
      System.out.println(myEnums.length);
      System.out.println(2);
    }

    @NeverClassInline
    enum MyEnum {
      A,
      B,
      C;
    }
  }

  static class EnumArrayReadWrite {

    public static void main(String[] args) {
      MyEnum[] myEnums = getArray();
      System.out.println(myEnums[1].ordinal());
      System.out.println(2);
      System.out.println(myEnums[0] == null);
      System.out.println("true");
      myEnums[0] = MyEnum.B;
      System.out.println(sum(myEnums));
      System.out.println(2);
    }

    @NeverInline
    public static MyEnum[] getArray() {
      MyEnum[] myEnums = new MyEnum[2];
      myEnums[1] = MyEnum.C;
      return myEnums;
    }

    @NeverInline
    public static int sum(MyEnum[] args) {
      return args.length;
    }

    @NeverClassInline
    enum MyEnum {
      A,
      B,
      C;
    }
  }

  static class EnumArrayNullRead {

    @SuppressWarnings("ConstantConditions")
    public static void main(String[] args) {
      try {
        System.out.println(((MyEnum[]) null)[0]);
      } catch (NullPointerException ignored) {
      }
    }

    @NeverClassInline
    enum MyEnum {
      A,
      B,
      C;
    }
  }

  static class Enum2DimArrayReadWrite {

    public static void main(String[] args) {
      MyEnum[][] myEnums = getArray();
      System.out.println(myEnums[1][1].ordinal());
      System.out.println(2);
      System.out.println(myEnums[0][0].ordinal());
      System.out.println(1);
      System.out.println(myEnums[0].length);
      System.out.println(2);
    }

    @NeverInline
    public static MyEnum[][] getArray() {
      MyEnum[][] myEnums = new MyEnum[2][2];
      myEnums[1][1] = MyEnum.C;
      myEnums[1][0] = MyEnum.A;
      myEnums[0][0] = MyEnum.B;
      myEnums[0][1] = MyEnum.A;
      return myEnums;
    }

    @NeverClassInline
    enum MyEnum {
      A,
      B,
      C;
    }
  }
}
