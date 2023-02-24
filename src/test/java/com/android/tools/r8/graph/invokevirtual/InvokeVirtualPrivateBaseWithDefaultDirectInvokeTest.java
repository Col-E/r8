// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.invokevirtual;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeVirtualPrivateBaseWithDefaultDirectInvokeTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public InvokeVirtualPrivateBaseWithDefaultDirectInvokeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private boolean isDefaultCfParameters() {
    return parameters.isCfRuntime() && parameters.getApiLevel().equals(AndroidApiLevel.B);
  }

  @Test
  public void testJvm() throws Exception {
    assumeTrue(isDefaultCfParameters());
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .apply(r -> assertResultIsCorrect(r, true));
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .apply(r -> assertResultIsCorrect(r, false));
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(I.class)
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/182189123): This should have the same behavior as D8.
        .applyIf(
            parameters.isCfRuntime(),
            r -> r.assertSuccessWithOutputLines("I::foo"),
            r -> assertResultIsCorrect(r, true));
  }

  public void assertResultIsCorrect(SingleTestRunResult<?> result, boolean nonDesugaredCf) {
    boolean isNotDesugared =
        (nonDesugaredCf && parameters.isCfRuntime())
            || parameters.canUseDefaultAndStaticInterfaceMethodsWhenDesugaring();
    // JDK 11 allows this incorrect dispatch for some reason.
    if (parameters.isCfRuntime()
        && parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK11)
        && isNotDesugared) {
      result.assertSuccessWithOutputLines("I::foo");
      return;
    }
    // TODO(b/152199517): Should become an illegal access on future DEX VM.
    if (parameters.isDexRuntime() && isNotDesugared) {
      result.assertSuccessWithOutputLines("I::foo");
      return;
    }
    result.assertFailureWithErrorThatThrows(IllegalAccessError.class);
  }

  @NoVerticalClassMerging
  public interface I {

    default void foo() {
      System.out.println("I::foo");
    }
  }

  @NoVerticalClassMerging
  public static class Base {

    private void foo() {
      System.out.println("Base::foo");
    }
  }

  public static class Sub extends Base implements I {}

  public static class Main {

    public static void main(String[] args) {
      runFoo(new Sub());
    }

    private static void runFoo(I i) {
      i.foo();
    }
  }
}
