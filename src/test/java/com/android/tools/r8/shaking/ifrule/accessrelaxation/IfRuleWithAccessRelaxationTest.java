// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.ifrule.accessrelaxation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isFinal;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPublic;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IfRuleWithAccessRelaxationTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void r8Test() throws Exception {
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addInnerClasses(IfRuleWithAccessRelaxationTest.class)
            .addKeepMainRule(TestClass.class)
            .addKeepRules(
                "-keep,allowaccessmodification class " + TestClass.class.getTypeName() + " {",
                "  int field;",
                "}",
                "-if class " + TestClass.class.getTypeName() + " { protected int field; }",
                "-keep class " + Unused1.class.getTypeName(),
                "-if class " + TestClass.class.getTypeName() + " {",
                "  private !final void privateMethod();",
                "}",
                "-keep class " + Unused2.class.getTypeName(),
                "-if class " + TestClass.class.getTypeName() + " {",
                "  protected void virtualMethod();",
                "}",
                "-keep class " + Unused3.class.getTypeName())
            .allowAccessModification()
            .enableInliningAnnotations()
            .enableNoMethodStaticizingAnnotations()
            .setMinApi(parameters.getApiLevel())
            .compile()
            .inspector();

    assertTrue(inspector.clazz(TestClass.class).isPublic());
    assertThat(inspector.clazz(TestClass.class).uniqueFieldWithOriginalName("field"), isPublic());
    assertThat(
        inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("privateMethod"),
        allOf(isPublic(), isFinal()));
    assertThat(
        inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("virtualMethod"), isPublic());

    assertThat(inspector.clazz(Unused1.class), isPresent());
    assertThat(inspector.clazz(Unused2.class), isPresent());
    assertThat(inspector.clazz(Unused3.class), isPresent());
  }

  protected static class TestClass {

    public static void main(String[] args) {
      TestClass obj = new TestClass();
      obj.privateMethod();
      obj.virtualMethod();
    }

    protected int field = 42;

    @NeverInline
    @NoMethodStaticizing
    private void privateMethod() {
      System.out.println("In privateMethod()");
    }

    @NeverInline
    protected void virtualMethod() {
      System.out.println("In virtualMethod()");
    }
  }

  static class Unused1 {}

  static class Unused2 {}

  static class Unused3 {}
}
