// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.enumunboxing;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.EnumSet;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FailingMethodEnumUnboxingTest extends EnumUnboxingTestBase {
  private static final Class<?>[] TESTS = {
    InstanceFieldPutObject.class,
    StaticFieldPutObject.class,
    EnumSetTest.class,
    FailingPhi.class,
    FailingReturnType.class,
    FailingParameterType.class
  };
  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public FailingMethodEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxingFailure() throws Exception {
    R8TestCompileResult compile =
        testForR8(parameters.getBackend())
            .addInnerClasses(FailingMethodEnumUnboxingTest.class)
            .addKeepMainRules(TESTS)
            .addKeepRules(enumKeepRules.getKeepRules())
            .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
            .addEnumUnboxingInspector(
                inspector ->
                    inspector.assertNotUnboxed(
                        InstanceFieldPutObject.MyEnum.class,
                        StaticFieldPutObject.MyEnum.class,
                        EnumSetTest.MyEnum.class,
                        FailingPhi.MyEnum.class,
                        FailingReturnType.MyEnum.class,
                        FailingParameterType.MyEnum.class))
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            // TODO(b/173398086): uniqueMethodWithName() does not work with signature changes.
            .addDontObfuscate()
            .setMinApi(parameters)
            .compile()
            .inspect(this::assertEnumsAsExpected);
    for (Class<?> main : TESTS) {
      compile
          .run(parameters.getRuntime(), main)
          .applyIf(
              main == EnumSetTest.class && enumKeepRules.getKeepRules().isEmpty(),
              // EnumSet and EnumMap cannot be used without the enumKeepRules.
              SingleTestRunResult::assertFailure,
              result -> result.assertSuccess().inspectStdOut(this::assertLines2By2Correct));
    }
  }

  private void assertEnumsAsExpected(CodeInspector inspector) {
    // Check all as expected (else we test nothing)
    assertEquals(
        1,
        inspector.clazz(InstanceFieldPutObject.class).getDexProgramClass().instanceFields().size());
    assertEquals(
        1, inspector.clazz(StaticFieldPutObject.class).getDexProgramClass().staticFields().size());
    assertTrue(
        inspector.clazz(FailingPhi.class).uniqueMethodWithOriginalName("switchOn").isPresent());
  }

  static class InstanceFieldPutObject {
    @NeverClassInline
    enum MyEnum {
      A,
      B,
      C
    }

    Object e;

    public static void main(String[] args) {
      InstanceFieldPutObject fieldPut = new InstanceFieldPutObject();
      fieldPut.setA();
      Object obj = new Object();
      put(fieldPut, obj);
      System.out.println(fieldPut.e);
      System.out.println(obj);
    }

    @NeverInline
    static void put(InstanceFieldPutObject object, Object value) {
      object.e = value;
    }

    void setA() {
      e = MyEnum.A;
    }
  }

  static class StaticFieldPutObject {
    @NeverClassInline
    enum MyEnum {
      A,
      B,
      C
    }

    static Object e;

    public static void main(String[] args) {
      setA();
      Object obj = new Object();
      e = obj;
      System.out.println(e);
      System.out.println(obj);
    }

    static void setA() {
      e = MyEnum.A;
    }
  }

  static class EnumSetTest {
    @NeverClassInline
    enum MyEnum {
      A,
      B,
      C
    }

    public static void main(String[] args) {
      EnumSet<MyEnum> es = EnumSet.allOf(MyEnum.class);
      System.out.println(es.size());
      System.out.println("3");
    }
  }

  static class FailingPhi {
    @NeverClassInline
    enum MyEnum {
      A,
      B,
      C
    }

    public static void main(String[] args) {
      System.out.println(switchOn(1));
      System.out.println("B");
      System.out.println(switchOn(2));
      System.out.println("class java.lang.Object");
    }
    // Avoid removing the switch entirely.
    @NeverInline
    static Object switchOn(int i) {
      switch (i) {
        case 0:
          return MyEnum.A;
        case 1:
          return MyEnum.B;
        default:
          return Object.class;
      }
    }
  }

  static class FailingReturnType {
    @NeverClassInline
    enum MyEnum {
      A,
      B,
      C
    }

    public static void main(String[] args) {
      System.out.println(returnObject(MyEnum.A) == MyEnum.A);
      System.out.println("true");
      System.out.println(returnObject(MyEnum.B) == MyEnum.B);
      System.out.println("true");
    }

    @NeverInline
    static Object returnObject(MyEnum e) {
      return System.currentTimeMillis() >= 0 ? e : new Object();
    }
  }

  static class FailingParameterType {
    @NeverClassInline
    enum MyEnum {
      A,
      B,
      C
    }

    public static void main(String[] args) {
      System.out.println(objectToInt(MyEnum.A));
      System.out.println("0");
      System.out.println(objectToInt(MyEnum.B));
      System.out.println("1");
    }

    @NeverInline
    static int objectToInt(Object e) {
      return e instanceof Enum ? ((Enum) e).ordinal() : e.hashCode();
    }
  }
}
