// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.ifrule.accessrelaxation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isFinal;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPublic;
import static org.hamcrest.CoreMatchers.allOf;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IfRuleWithAccessRelaxationTest extends TestBase {

  private final Backend backend;

  public IfRuleWithAccessRelaxationTest(Backend backend) {
    this.backend = backend;
  }

  @Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  @Test
  public void r8Test() throws Exception {
    CodeInspector inspector =
        testForR8(backend)
            .addInnerClasses(IfRuleWithAccessRelaxationTest.class)
            .addKeepMainRule(TestClass.class)
            .addKeepRules(
                "-keep class " + TestClass.class.getTypeName() + " { int field; }",
                "-if class " + TestClass.class.getTypeName() + " { protected int field; }",
                "-keep class " + Unused1.class.getTypeName(),
                "-if class " + TestClass.class.getTypeName() + " {",
                "  private !final void privateMethod();",
                "}",
                "-keep class " + Unused2.class.getTypeName(),
                "-if class " + TestClass.class.getTypeName() + " {",
                "  protected void virtualMethod();",
                "}",
                "-keep class " + Unused3.class.getTypeName(),
                "-allowaccessmodification")
            .enableInliningAnnotations()
            .compile()
            .inspector();

    assertTrue(inspector.clazz(TestClass.class).isPublic());
    assertThat(inspector.clazz(TestClass.class).uniqueFieldWithName("field"), isPublic());
    assertThat(
        inspector.clazz(TestClass.class).uniqueMethodWithName("privateMethod"),
        allOf(isPublic(), isFinal()));
    assertThat(inspector.clazz(TestClass.class).uniqueMethodWithName("virtualMethod"), isPublic());

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
