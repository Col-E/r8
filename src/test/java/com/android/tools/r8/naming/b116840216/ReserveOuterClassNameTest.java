// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.b116840216;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@NoHorizontalClassMerging
class Outer {

  @NoHorizontalClassMerging
  static class Inner {
    @NeverInline
    static void foo() {
      System.out.println("Inner.foo");
    }
  }

  @NeverInline
  static void bar() {
    System.out.println("Outer.bar");
  }
}

class TestMain {
  public static void main(String[] args) {
    Outer.Inner.foo();
    Outer.bar();
  }
}

@RunWith(Parameterized.class)
public class ReserveOuterClassNameTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private void runTest(boolean keepOuterName) throws Exception {
    CodeInspector inspector =
        testForR8Compat(parameters.getBackend())
            .addProgramClasses(TestMain.class, Outer.class, Outer.Inner.class)
            .addKeepAttributeInnerClassesAndEnclosingMethod()
            .addKeepAttributeSignature()
            .addKeepMainRule(TestMain.class)
            .addKeepRules(
                // Note that reproducing b/116840216 relies on the order of following rules that
                // cause
                // the visiting of classes during class minification to be Outer$Inner before Outer.
                "-keepnames class " + Outer.class.getCanonicalName() + "$Inner",
                keepOuterName ? "-keepnames class " + Outer.class.getCanonicalName() : "")
            .enableInliningAnnotations()
            .enableNoHorizontalClassMergingAnnotations()
            .setMinApi(parameters)
            .compile()
            .inspector();

    ClassSubject mainSubject = inspector.clazz(TestMain.class);
    assertThat(mainSubject, isPresentAndNotRenamed());
    MethodSubject mainMethod = mainSubject.mainMethod();
    assertThat(mainMethod, isPresentAndNotRenamed());

    ClassSubject outer = inspector.clazz(Outer.class);
    assertThat(outer, isPresentAndNotRenamed());
    MethodSubject bar = outer.method("void", "bar", ImmutableList.of());
    assertThat(bar, isPresentAndRenamed());

    ClassSubject inner = inspector.clazz(Outer.Inner.class);
    assertThat(inner, isPresentAndNotRenamed());
    MethodSubject foo = inner.method("void", "foo", ImmutableList.of());
    assertThat(foo, isPresentAndRenamed());
  }

  @Test
  public void test_keepOuterName() throws Exception {
    runTest(true);
  }

  @Test
  public void test_keepInnerNameOnly() throws Exception {
    runTest(false);
  }
}
