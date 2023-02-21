// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.clinit;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InterfaceInitializedByImplementationTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withAllRuntimes()
        // Ensure default interface methods are supported.
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.N)
        .build();
  }

  public InterfaceInitializedByImplementationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(InterfaceInitializedByImplementationTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .setMinApi(parameters)
        .addDontObfuscate()
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    // Check that A is not removed.
    ClassSubject aClasssubject = inspector.clazz(A.class);
    assertThat(aClasssubject, isPresent());

    // Check that I.<clinit>() is not removed.
    ClassSubject iClassSubject = inspector.clazz(I.class);
    assertThat(iClassSubject, isPresent());
    assertThat(iClassSubject.clinit(), isPresent());

    // Check that B.<clinit>() is not removed.
    ClassSubject bClassSubject = inspector.clazz(B.class);
    assertThat(bClassSubject, isPresent());
    assertThat(bClassSubject.clinit(), isPresent());
  }

  static class TestClass {

    public static void main(String[] args) {
      new A().m();
    }
  }

  interface I {

    B B_INSTANCE = new B();

    // TODO(b/144266257): If tree shaking removes this method, then I.<clinit>() won't be run when
    //  A is being class initialized.
    @NeverInline
    @NoMethodStaticizing
    default void m() {
      System.out.println(" world!");
    }
  }

  static class A implements I {}

  static class B {

    static {
      System.out.print("Hello");
    }
  }
}
