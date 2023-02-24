// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.ifrule.classstaticizer;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.shaking.ifrule.classstaticizer.IfRuleWithClassStaticizerTest.StaticizerCandidate.Companion;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IfRuleWithClassStaticizerTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("In method()");

    if (parameters.isCfRuntime()) {
      testForJvm(parameters)
          .addTestClasspath()
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(expectedOutput);
    }

    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addInnerClasses(IfRuleWithClassStaticizerTest.class)
            .addKeepMainRule(TestClass.class)
            .addKeepRules(
                "-if class " + Companion.class.getTypeName() + " {",
                "  public !static void method();",
                "}",
                "-keep class " + Unused.class.getTypeName())
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .setMinApi(parameters)
            .run(parameters.getRuntime(), TestClass.class)
            .assertSuccessWithOutput(expectedOutput)
            .inspector();

    ClassSubject classSubject = inspector.clazz(StaticizerCandidate.class);
    assertThat(classSubject, isAbsent());

    if (parameters.isCfRuntime()) {
      // The class staticizer is not enabled for CF.
      assertThat(inspector.clazz(Unused.class), isPresent());
    } else {
      assert parameters.isDexRuntime();

      // There should be a static method after staticizing.
      ClassSubject companionClassSubject = inspector.clazz(StaticizerCandidate.Companion.class);
      assertThat(companionClassSubject, isPresent());
      List<FoundMethodSubject> staticMethods =
          companionClassSubject.allMethods().stream()
              .filter(method -> method.isStatic() && !method.isClassInitializer())
              .collect(Collectors.toList());
      assertEquals(1, staticMethods.size());
      assertEquals("void method()", staticMethods.get(0).getOriginalSignature().toString());

      assertThat(inspector.clazz(Unused.class), isPresent());
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      StaticizerCandidate.companion.method();
    }
  }

  @NeverClassInline
  static class StaticizerCandidate {

    static final Companion companion = new Companion();

    @NeverClassInline
    static class Companion {

      @NeverInline
      public void method() {
        System.out.println("In method()");
      }
    }
  }

  static class Unused {}
}
