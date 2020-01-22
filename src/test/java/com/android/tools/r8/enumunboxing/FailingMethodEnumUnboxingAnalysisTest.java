// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.EnumSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FailingMethodEnumUnboxingAnalysisTest extends EnumUnboxingTestBase {

  private static final Class<?>[] FAILURES = {
    NullCheck.class,
    Check.class,
    FieldPut.class,
    FieldPutObject.class,
    ToString.class,
    EnumSetTest.class,
    FailingPhi.class
  };

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return enumUnboxingTestParameters();
  }

  public FailingMethodEnumUnboxingAnalysisTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testEnumUnboxingFailure() throws Exception {
    R8FullTestBuilder r8FullTestBuilder =
        testForR8(parameters.getBackend())
            .addInnerClasses(FailingMethodEnumUnboxingAnalysisTest.class);
    for (Class<?> failure : FAILURES) {
      r8FullTestBuilder.addKeepMainRule(failure);
    }
    R8TestCompileResult compile =
        r8FullTestBuilder
            .addKeepRules(KEEP_ENUM)
            .addOptionsModification(this::enableEnumOptions)
            .enableInliningAnnotations()
            .addOptionsModification(
                // Disabled to avoid toString() being removed.
                opt -> opt.enableEnumValueOptimization = false)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .inspect(this::assertEnumsAsExpected);
    for (Class<?> failure : FAILURES) {
      R8TestRunResult run =
          compile
              .inspectDiagnosticMessages(
                  m ->
                      assertEnumIsBoxed(
                          failure.getDeclaredClasses()[0], failure.getSimpleName(), m))
              .run(parameters.getRuntime(), failure)
              .assertSuccess();
      assertLines2By2Correct(run.getStdOut());
    }
  }

  private void assertEnumsAsExpected(CodeInspector inspector) {
    // Check all as expected (else we test nothing)
    assertTrue(inspector.clazz(NullCheck.class).uniqueMethodWithName("nullCheck").isPresent());
    assertTrue(inspector.clazz(Check.class).uniqueMethodWithName("check").isPresent());

    assertEquals(1, inspector.clazz(FieldPut.class).allInstanceFields().size());
    assertEquals(1, inspector.clazz(FieldPutObject.class).allInstanceFields().size());

    assertTrue(inspector.clazz(FailingPhi.class).uniqueMethodWithName("switchOn").isPresent());
  }

  @SuppressWarnings("ConstantConditions")
  static class NullCheck {

    enum MyEnum {
      A,
      B,
      C
    }

    public static void main(String[] args) {
      System.out.println(nullCheck(MyEnum.A));
      System.out.println(false);
      System.out.println(nullCheck(null));
      System.out.println(true);
    }

    // Do not resolve the == with constants after inlining.
    @NeverInline
    static boolean nullCheck(MyEnum e) {
      return e == null;
    }
  }

  static class Check {

    enum MyEnum {
      A,
      B,
      C
    }

    public static void main(String[] args) {
      MyEnum e1 = MyEnum.A;
      System.out.println(check(e1));
      System.out.println(false);
    }

    // Do not resolve the == with constants after inlining.
    @NeverInline
    static boolean check(MyEnum e) {
      return e == MyEnum.B;
    }
  }

  static class FieldPut {

    enum MyEnum {
      A,
      B,
      C
    }

    MyEnum e;

    public static void main(String[] args) {
      FieldPut fieldPut = new FieldPut();
      fieldPut.setA();
      System.out.println(fieldPut.e.ordinal());
      System.out.println(0);
    }

    void setA() {
      e = MyEnum.A;
    }
  }

  static class FieldPutObject {

    enum MyEnum {
      A,
      B,
      C
    }

    Object e;

    public static void main(String[] args) {
      FieldPutObject fieldPut = new FieldPutObject();
      fieldPut.setA();
      Object obj = new Object();
      fieldPut.e = obj;
      System.out.println(fieldPut.e);
      System.out.println(obj);
    }

    void setA() {
      e = MyEnum.A;
    }
  }

  static class ToString {

    enum MyEnum {
      A,
      B,
      C
    }

    public static void main(String[] args) {
      MyEnum e1 = MyEnum.A;
      System.out.println(e1.toString());
      System.out.println("A");
    }
  }

  static class EnumSetTest {

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
}
