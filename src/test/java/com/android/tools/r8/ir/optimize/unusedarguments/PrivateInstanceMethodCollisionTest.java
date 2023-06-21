// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.unusedarguments;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.KeepConstantArguments;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PrivateInstanceMethodCollisionTest extends TestBase {

  private final TestParameters parameters;
  private final boolean minification;
  private final boolean allowAccessModification;

  @Parameters(name = "{0}, minification: {1}, allowaccessmodification: {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  public PrivateInstanceMethodCollisionTest(
      TestParameters parameters, boolean minification, boolean allowAccessModification) {
    this.parameters = parameters;
    this.minification = minification;
    this.allowAccessModification = allowAccessModification;
  }

  @Test
  public void b139769782() throws Exception {
    String expectedOutput = StringUtils.lines("A#foo(used)", "A#foo(used, Object)");

    if (parameters.isCfRuntime() && !minification && !allowAccessModification) {
      testForJvm(parameters)
          .addTestClasspath()
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(expectedOutput);
    }

    testForR8(parameters.getBackend())
        .addInnerClasses(PrivateInstanceMethodCollisionTest.class)
        .addKeepMainRule(TestClass.class)
        .enableConstantArgumentAnnotations()
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .minification(minification)
        .allowAccessModification(allowAccessModification)
        .applyIf(
            allowAccessModification,
            testBuilder -> testBuilder.addNoAccessModificationAnnotation(),
            testBuilder -> testBuilder.enableNoAccessModificationAnnotationsForMembers())
        .setMinApi(parameters)
        .compile()
        .inspect(this::verifyUnusedArgumentsRemovedAndNoCollisions)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  private void verifyUnusedArgumentsRemovedAndNoCollisions(CodeInspector inspector) {
    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());

    if (allowAccessModification) {
      assertEquals(2, aClassSubject.allMethods(FoundMethodSubject::isVirtual).size());
      String name = null;
      for (FoundMethodSubject m : aClassSubject.allMethods(FoundMethodSubject::isVirtual)) {
        assertEquals(1, m.getMethod().getReference().proto.parameters.size());
        if (name == null) {
          name = m.getFinalName();
        } else {
          assertNotEquals(name, m.getFinalName());
        }
      }
    } else {
      assertEquals(1, aClassSubject.allMethods(FoundMethodSubject::isPrivate).size());
      MethodSubject privateFoo = aClassSubject.allMethods(FoundMethodSubject::isPrivate).get(0);
      assertThat(privateFoo, isPresent());

      assertEquals(1, aClassSubject.allMethods(FoundMethodSubject::isVirtual).size());
      MethodSubject virtualFoo = aClassSubject.allMethods(FoundMethodSubject::isVirtual).get(0);
      assertThat(virtualFoo, isPresent());

      assertNotEquals(privateFoo.getFinalName(), virtualFoo.getFinalName());
    }
  }

  static class TestClass {
    public static void main(String... args) {
      A obj = new A();
      obj.foo("used");
      obj.foo("used", null);
    }
  }

  @NeverClassInline
  static class A {

    @KeepConstantArguments
    @NeverInline
    @NoAccessModification
    @NoMethodStaticizing
    private void foo(String used) {
      System.out.println("A#foo(" + used + ")");
    }

    @KeepConstantArguments
    @NeverInline
    @NoAccessModification
    @NoMethodStaticizing
    void foo(String used, Object unused) {
      System.out.println("A#foo(" + used + ", Object)");
    }
  }
}
