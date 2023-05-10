// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner.sync;

import static com.android.tools.r8.utils.codeinspector.Matchers.hasDefaultConstructor;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InlineConstructorsWithMonitors extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InlineConstructorsWithMonitors(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(InlineConstructorsWithMonitors.class)
        .addKeepMainRule(TestClass.class)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector
                    .applyIf(
                        !parameters.canHaveIssueWithInlinedMonitors(),
                        i -> i.assertMergedInto(Foo.class, Bar.class))
                    .assertNoOtherClassesMerged())
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("monitor", "monitor2")
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());
    ClassSubject fooClassSubject = inspector.clazz(Foo.class);
    ClassSubject barClassSubject = inspector.clazz(Bar.class);
    if (parameters.canHaveIssueWithInlinedMonitors()) {
      // On M and below we don't want to merge constructors when both have monitors. See b/238399429
      assertThat(
          fooClassSubject,
          notIf(isPresent(), parameters.canInitNewInstanceUsingSuperclassConstructor()));
      assertThat(
          fooClassSubject,
          notIf(
              hasDefaultConstructor(), parameters.canInitNewInstanceUsingSuperclassConstructor()));
      assertThat(barClassSubject, isPresent());
      assertThat(barClassSubject, hasDefaultConstructor());
    } else {
      assertThat(fooClassSubject, isAbsent());
      assertThat(barClassSubject, isPresent());
      assertThat(barClassSubject.uniqueInstanceInitializer(), isPresent());
    }
  }

  static class Foo {
    public Foo() {
      Object o = new Object();
      synchronized (o) {
        System.out.println("monitor");
      }
    }
  }

  static class Bar {
    public Bar() {
      Object o = new String("Foo");
      synchronized (o) {
        System.out.println("monitor2");
      }
    }
  }

  static class TestClass {
    public static void main(String[] args) {
      new Foo();
      new Bar();
    }
  }
}
