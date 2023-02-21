// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.devirtualize;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// This is a reproduction of b/173812804.
@RunWith(Parameterized.class)
public class PrivateOverridePublicizerDevirtualizerTest extends TestBase {

  private final TestParameters parameters;
  private final String[] EXPECTED = new String[] {"A::foo", "B::foo"};

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public PrivateOverridePublicizerDevirtualizerTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(PrivateOverridePublicizerDevirtualizerTest.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(PrivateOverridePublicizerDevirtualizerTest.class)
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
        .addKeepMainRule(Main.class)
        .allowAccessModification()
        .addDontObfuscate()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject classA = inspector.clazz(A.class);
              assertThat(classA, isPresent());
              MethodSubject fooA = classA.uniqueMethodWithOriginalName("foo");
              assertThat(fooA, isPresent());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @NeverClassInline
  @NoVerticalClassMerging
  public static class A {
    @NeverInline
    private void foo() {
      System.out.println("A::foo"); // <-- this is made public by the publicizer
    }

    public void callFoo() {
      foo();
    }
  }

  @NeverClassInline
  public static class B extends A {
    @NeverInline
    private void foo() {
      System.out.println("B::foo");
    }

    @Override
    @NeverInline
    public void callFoo() {
      // We end up inlining A::callFoo, and then, because the call to A::foo is public, we end up
      // target B::foo in the virtualizer.
      super.callFoo();
      foo();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new B().callFoo();
    }
  }
}
