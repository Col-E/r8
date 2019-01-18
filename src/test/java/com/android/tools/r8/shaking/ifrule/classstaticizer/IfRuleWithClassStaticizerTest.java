// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.ifrule.classstaticizer;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IfRuleWithClassStaticizerTest extends TestBase {

  private final Backend backend;

  @Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public IfRuleWithClassStaticizerTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("In method()");

    if (backend == Backend.CF) {
      testForJvm().addTestClasspath().run(TestClass.class).assertSuccessWithOutput(expectedOutput);
    }

    CodeInspector inspector =
        testForR8(backend)
            .addInnerClasses(IfRuleWithClassStaticizerTest.class)
            .addKeepMainRule(TestClass.class)
            .addKeepRules(
                "-if class " + StaticizerCandidate.Companion.class.getTypeName() + " {",
                "  public !static void method();",
                "}",
                "-keep class " + Unused.class.getTypeName())
            .enableInliningAnnotations()
            .enableClassInliningAnnotations()
            .run(TestClass.class)
            .assertSuccessWithOutput(expectedOutput)
            .inspector();

    ClassSubject classSubject = inspector.clazz(StaticizerCandidate.class);
    assertThat(classSubject, isPresent());

    if (backend == Backend.CF) {
      // The class staticizer is not enabled for CF.
      assertThat(inspector.clazz(Unused.class), isPresent());
    } else {
      assert backend == Backend.DEX;

      // There should be a static method on StaticizerCandidate after staticizing.
      List<FoundMethodSubject> staticMethods =
          classSubject.allMethods().stream()
              .filter(method -> method.isStatic() && !method.isClassInitializer())
              .collect(Collectors.toList());
      assertEquals(1, staticMethods.size());
      assertEquals(
          "void " + StaticizerCandidate.Companion.class.getTypeName() + ".method()",
          staticMethods.get(0).getOriginalSignature().toString());

      // The Companion class should not be present after staticizing.
      assertThat(inspector.clazz(StaticizerCandidate.Companion.class), not(isPresent()));

      // TODO(b/122867080): The Unused class should be present due to the -if rule.
      assertThat(inspector.clazz(Unused.class), not(isPresent()));
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
