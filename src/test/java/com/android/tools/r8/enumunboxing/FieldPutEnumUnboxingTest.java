// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import static org.junit.Assert.assertEquals;

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
public class FieldPutEnumUnboxingTest extends EnumUnboxingTestBase {

  private static final Class<?>[] INPUTS =
      new Class<?>[] {InstanceFieldPut.class, StaticFieldPut.class};

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public FieldPutEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    R8TestCompileResult compile =
        testForR8(parameters.getBackend())
            .addInnerClasses(FieldPutEnumUnboxingTest.class)
            .addKeepMainRules(INPUTS)
            .addKeepRules(enumKeepRules.getKeepRules())
            .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
            .addEnumUnboxingInspector(
                inspector ->
                    inspector.assertUnboxed(
                        InstanceFieldPut.MyEnum.class, StaticFieldPut.MyEnum.class))
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .setMinApi(parameters)
            .addDontObfuscate()
            .compile()
            .inspect(
                i -> {
                  assertEquals(
                      1,
                      i.clazz(InstanceFieldPut.class).getDexProgramClass().instanceFields().size());
                  assertEquals(
                      1, i.clazz(StaticFieldPut.class).getDexProgramClass().staticFields().size());
                });

    for (Class<?> main : INPUTS) {
      compile
          .run(parameters.getRuntime(), main)
          .assertSuccess()
          .inspectStdOut(this::assertLines2By2Correct);
    }
  }

  static class InstanceFieldPut {

    @NeverClassInline
    enum MyEnum {
      A,
      B,
      C
    }

    MyEnum e = null;

    public static void main(String[] args) {
      InstanceFieldPut fieldPut = new InstanceFieldPut();
      System.out.println(fieldPut.e == null);
      System.out.println("true");
      fieldPut.setA();
      System.out.println(fieldPut.e.ordinal());
      System.out.println(0);
      fieldPut.setB();
      System.out.println(fieldPut.e.ordinal());
      System.out.println(1);
    }

    @NeverInline
    void setA() {
      e = MyEnum.A;
    }

    @NeverInline
    void setB() {
      e = MyEnum.B;
    }
  }

  static class StaticFieldPut {

    @NeverClassInline
    enum MyEnum {
      A,
      B,
      C
    }

    static MyEnum e = null;

    public static void main(String[] args) {
      System.out.println(StaticFieldPut.e == null);
      System.out.println("true");
      setA();
      System.out.println(StaticFieldPut.e.ordinal());
      System.out.println(0);
      setB();
      System.out.println(StaticFieldPut.e.ordinal());
      System.out.println(1);
    }

    @NeverInline
    static void setA() {
      StaticFieldPut.e = MyEnum.A;
    }

    @NeverInline
    static void setB() {
      StaticFieldPut.e = MyEnum.B;
    }
  }
}
