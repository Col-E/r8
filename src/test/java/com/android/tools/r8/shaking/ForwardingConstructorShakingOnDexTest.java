// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.KeepConstantArguments;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ForwardingConstructorShakingOnDexTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> options.testing.enableRedundantConstructorBridgeRemoval = true)
        .enableConstantArgumentAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A", "A(String)", "B", "B(String)", "C", "C(String)");
  }

  private void inspect(CodeInspector inspector) {
    boolean canHaveNonReboundConstructorInvoke =
        parameters.isDexRuntime()
            && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.L);

    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());
    assertEquals(2, aClassSubject.allMethods(FoundMethodSubject::isInstanceInitializer).size());

    ClassSubject bClassSubject = inspector.clazz(B.class);
    assertThat(bClassSubject, isPresent());
    assertEquals(
        canHaveNonReboundConstructorInvoke ? 0 : 2,
        bClassSubject.allMethods(FoundMethodSubject::isInstanceInitializer).size());

    ClassSubject cClassSubject = inspector.clazz(C.class);
    assertThat(cClassSubject, isPresent());
    assertEquals(
        canHaveNonReboundConstructorInvoke ? 0 : 2,
        cClassSubject.allMethods(FoundMethodSubject::isInstanceInitializer).size());
  }

  static class Main {

    public static void main(String[] args) {
      new A();
      new A("A(String)");
      new B();
      new B("B(String)");
      new C();
      new C("C(String)");
    }
  }

  @NeverClassInline
  @NoVerticalClassMerging
  static class A {

    A() {
      if (this instanceof C) {
        System.out.println("C");
      } else if (this instanceof B) {
        System.out.println("B");
      } else {
        System.out.println("A");
      }
    }

    A(String string) {
      System.out.println(string);
    }
  }

  @NeverClassInline
  @NoVerticalClassMerging
  static class B extends A {

    // These constructors simply forward the arguments to the parent constructor.
    // They can be removed when compiling for dex and the API is above Dalvik.
    B() {}

    B(String string) {
      super(string);
    }
  }

  @NeverClassInline
  @NoVerticalClassMerging
  static class C extends B {

    // Ditto.
    C() {}

    @KeepConstantArguments
    C(String string) {
      super(string);
    }
  }
}
