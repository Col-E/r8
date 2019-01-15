// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.ifrule.interfacemethoddesugaring;

import static com.android.tools.r8.ir.desugar.InterfaceMethodRewriter.COMPANION_CLASS_NAME_SUFFIX;
import static com.android.tools.r8.ir.desugar.InterfaceMethodRewriter.DEFAULT_METHOD_PREFIX;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPublic;
import static com.android.tools.r8.utils.codeinspector.Matchers.isStatic;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;

public class IfRuleWithInterfaceMethodDesugaringTest extends TestBase {

  @Test
  public void test() throws Exception {
    String expectedOutput =
        StringUtils.lines("In Interface.staticMethod()", "In Interface.virtualMethod()");

    testForJvm().addTestClasspath().run(TestClass.class).assertSuccessWithOutput(expectedOutput);

    CodeInspector inspector =
        testForR8(Backend.DEX)
            .addInnerClasses(IfRuleWithInterfaceMethodDesugaringTest.class)
            .addKeepMainRule(TestClass.class)
            .addKeepRules(
                "-if class " + Interface.class.getTypeName() + " {",
                "  !public static void staticMethod();",
                "}",
                "-keep class " + Unused1.class.getTypeName(),
                "-if class " + Interface.class.getTypeName() + " {",
                "  !public !static void virtualMethod();",
                "}",
                "-keep class " + Unused2.class.getTypeName())
            .enableInliningAnnotations()
            .enableClassInliningAnnotations()
            .enableMergeAnnotations()
            .setMinApi(AndroidApiLevel.M)
            .run(TestClass.class)
            .assertSuccessWithOutput(expectedOutput)
            .inspector();

    ClassSubject classSubject =
        inspector.clazz(Interface.class.getTypeName() + COMPANION_CLASS_NAME_SUFFIX);
    assertThat(classSubject, isPresent());
    assertEquals(2, classSubject.allMethods().size());

    MethodSubject staticMethodSubject = classSubject.uniqueMethodWithName("staticMethod");
    assertThat(staticMethodSubject, allOf(isPresent(), isPublic(), isStatic()));

    // TODO(b/122867087): Should not be necessary to use `DEFAULT_METHOD_PREFIX`.
    MethodSubject virtualMethodSubject =
        classSubject.uniqueMethodWithName(DEFAULT_METHOD_PREFIX + "virtualMethod");
    assertThat(virtualMethodSubject, allOf(isPresent(), isPublic(), isStatic()));

    // TODO(b/122875545): The Unused class should be present due to the -if rule.
    assertThat(inspector.clazz(Unused1.class), not(isPresent()));
    assertThat(inspector.clazz(Unused2.class), not(isPresent()));
  }

  static class TestClass {

    public static void main(String[] args) {
      Interface.staticMethod();
      new InterfaceImpl().virtualMethod();
    }
  }

  @NeverClassInline
  @NeverMerge
  interface Interface {

    @NeverInline
    static void staticMethod() {
      System.out.println("In Interface.staticMethod()");
    }

    @NeverInline
    default void virtualMethod() {
      System.out.println("In Interface.virtualMethod()");
    }
  }

  @NeverClassInline
  static class InterfaceImpl implements Interface {}

  static class Unused1 {}

  static class Unused2 {}
}
