// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.ifrule.interfacemethoddesugaring;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPublic;
import static com.android.tools.r8.utils.codeinspector.Matchers.isStatic;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IfRuleWithInterfaceMethodDesugaringTest extends TestBase {

  private final TestParameters parameters;

  private static final String STATIC_STR = "In Interface.staticMethod()";
  private static final String VIRTUAL_STR = "In Interface.virtualMethod()";
  private static final String EXPECTED_OUTPUT = StringUtils.lines(STATIC_STR, VIRTUAL_STR);

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimes()
        .withDexRuntimes()
        .withApiLevel(AndroidApiLevel.M)
        .build();
  }

  public IfRuleWithInterfaceMethodDesugaringTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void test() throws Exception {
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addInnerClasses(IfRuleWithInterfaceMethodDesugaringTest.class)
            .addKeepMainRule(TestClass.class)
            .addKeepRules(
                "-if class " + Interface.class.getTypeName() + " {",
                "  static void staticMethod();",
                "}",
                "-keep class " + Unused1.class.getTypeName(),
                "-if class " + Interface.class.getTypeName() + " {",
                "  !static void virtualMethod();",
                "}",
                "-keep class " + Unused2.class.getTypeName())
            .allowUnusedProguardConfigurationRules(parameters.isDexRuntime())
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .enableNoVerticalClassMergingAnnotations()
            .setMinApi(parameters)
            .run(parameters.getRuntime(), TestClass.class)
            .assertSuccessWithOutput(EXPECTED_OUTPUT)
            .inspector();

    if (parameters.isCfRuntime()) {
      ClassSubject itfClass = inspector.clazz(Interface.class.getTypeName());
      assertThat(itfClass, isPresent());
      assertThat(itfClass.uniqueMethodWithOriginalName("staticMethod"), isPresent());
      assertThat(itfClass.uniqueMethodWithOriginalName("virtualMethod"), isPresent());
      assertThat(inspector.clazz(Unused1.class), isPresent());
      assertThat(inspector.clazz(Unused2.class), isPresent());
      return;
    }

    ClassSubject classSubject = inspector.clazz(Interface.class.getTypeName()).toCompanionClass();
    assertThat(classSubject, isPresent());

    // NeverInline is only applicable to the static method at this point (could change).
    assertEquals(1, classSubject.allMethods().size());
    MethodSubject staticMethodSubject = classSubject.uniqueMethodWithOriginalName("staticMethod");
    assertThat(staticMethodSubject, allOf(isPresent(), isPublic(), isStatic()));
    assertTrue(staticMethodSubject.streamInstructions().anyMatch(i -> i.isConstString(STATIC_STR)));

    // The virtual method is inlined as @NeverInline does not apply at this point (could change).
    assertTrue(
        inspector
            .clazz(TestClass.class)
            .mainMethod()
            .streamInstructions()
            .anyMatch(i -> i.isConstString(VIRTUAL_STR)));

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
  @NoVerticalClassMerging
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
