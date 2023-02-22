// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.keepclassmembers;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeepClassMembersRuleOnIndirectlyInstantiatedClassTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(KeepClassMembersRuleOnIndirectlyInstantiatedClassTest.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules(
            "-keepclassmembers class " + A.class.getTypeName() + " {",
            "  java.lang.String greeting;",
            "}")
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::verifyFieldIsPresent)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void verifyFieldIsPresent(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(A.class);
    assertThat(classSubject, isPresent());

    FieldSubject fieldSubject = classSubject.uniqueFieldWithOriginalName("greeting");
    assertThat(fieldSubject, isPresentAndNotRenamed());
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      B b = new B();
      reflectivelyWriteFieldWithName(b, "greeting", "Hello world!");
      System.out.println(reflectivelyReadFieldWithName(b, "greeting"));
    }

    @NeverInline
    private static void reflectivelyWriteFieldWithName(Object o, String fieldName, String value)
        throws Exception {
      o.getClass().getField(fieldName).set(o, value);
    }

    @NeverInline
    private static String reflectivelyReadFieldWithName(Object o, String fieldName)
        throws Exception {
      return (String) o.getClass().getField(fieldName).get(o);
    }
  }

  @NoVerticalClassMerging
  static class A {

    public String greeting;
  }

  static class B extends A {}
}
