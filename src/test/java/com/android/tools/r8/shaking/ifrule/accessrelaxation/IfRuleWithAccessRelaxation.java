// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.ifrule.accessrelaxation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IfRuleWithAccessRelaxation extends TestBase {

  private final Backend backend;

  public IfRuleWithAccessRelaxation(Backend backend) {
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
            .addInnerClasses(IfRuleWithAccessRelaxation.class)
            .addKeepRules(
                "-keep class " + TestClass.class.getTypeName() + " { int field; void method(); }",
                "-if class " + TestClass.class.getTypeName() + " { protected int field; }",
                "-keep class " + Unused1.class.getTypeName(),
                "-if class " + TestClass.class.getTypeName() + " { protected void method(); }",
                "-keep class " + Unused2.class.getTypeName(),
                "-allowaccessmodification")
            .compile()
            .inspector();

    assertTrue(inspector.clazz(TestClass.class).isPublic());
    assertTrue(inspector.clazz(TestClass.class).uniqueMethodWithName("method").isPublic());
    assertTrue(inspector.clazz(TestClass.class).uniqueFieldWithName("field").isPublic());

    assertThat(inspector.clazz(Unused1.class), isPresent());
    assertThat(inspector.clazz(Unused2.class), isPresent());
  }

  protected static class TestClass {

    protected int field = 42;

    protected void method() {}
  }

  static class Unused1 {}

  static class Unused2 {}
}
