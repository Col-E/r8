// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

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
public class RetainIndirectlyReferencedConstructorShakingOnDexTest extends TestBase {

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
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A", "B");
  }

  private void inspect(CodeInspector inspector) {
    boolean canHaveNonReboundConstructorInvoke =
        parameters.isDexRuntime()
            && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.L);

    // A.<init> should be retained despite the fact that there is no invoke-direct in the program
    // that directly targets A.<init> when B.<init> is removed.
    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());
    assertEquals(1, aClassSubject.allMethods(FoundMethodSubject::isInstanceInitializer).size());

    ClassSubject bClassSubject = inspector.clazz(B.class);
    assertThat(bClassSubject, isPresent());
    assertEquals(
        canHaveNonReboundConstructorInvoke ? 0 : 1,
        bClassSubject.allMethods(FoundMethodSubject::isInstanceInitializer).size());
  }

  static class Main {

    public static void main(String[] args) {
      // This uses an invoke-direct instruction that targets B.<init>(). If we remove B.<init>()
      // when compiling for dex, and keep the invoke-direct instruction targeting B.<init>(), it is
      // important that tree shaking traces A.<init>(), by resolving the invoke in the Enqueuer,
      // instead of trying to lookup <init>() directly on B. Otherwise, the program will fail with
      // NSME.
      System.out.println(new B());
    }
  }

  @NoVerticalClassMerging
  abstract static class A {

    A() {
      System.out.println("A");
    }
  }

  @NoVerticalClassMerging
  static class B extends A {

    // This constructor simply forward the arguments to the parent constructor.
    // It can be removed when compiling for dex and the API is above Dalvik.
    B() {}

    @Override
    public String toString() {
      return "B";
    }
  }
}
