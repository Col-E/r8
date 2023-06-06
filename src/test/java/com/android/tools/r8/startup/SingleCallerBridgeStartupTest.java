// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.startup;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.startup.profile.ExternalStartupItem;
import com.android.tools.r8.startup.profile.ExternalStartupMethod;
import com.android.tools.r8.startup.utils.StartupTestingUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Regression test for b/285021603. */
@RunWith(Parameterized.class)
public class SingleCallerBridgeStartupTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    // Create a startup profile with method A.bar() and A.callBarInStartup() to allow inlining
    // of A.bar() into A.callBarInStartup().
    MethodReference barMethod = Reference.methodFromMethod(A.class.getDeclaredMethod("bar"));
    Collection<ExternalStartupItem> startupProfile =
        ImmutableList.of(
            ExternalStartupMethod.builder().setMethodReference(barMethod).build(),
            ExternalStartupMethod.builder()
                .setMethodReference(
                    Reference.methodFromMethod(A.class.getDeclaredMethod("callBarInStartup")))
                .build());
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .apply(testBuilder -> StartupTestingUtils.addStartupProfile(testBuilder, startupProfile))
        .addOptionsModification(
            options ->
                // Simulate proto optimization where we allow reprocessing of inlinee.
                options.inlinerOptions().applyInliningToInlineePredicateForTesting =
                    (appView, inlinee, inliningDepth) ->
                        inlinee.getMethodReference().equals(barMethod))
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              // Assert that foo is not inlined.
              ClassSubject A = inspector.clazz(A.class);
              assertThat(A, isPresent());
              assertThat(A.uniqueMethodWithOriginalName("foo"), isPresent());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A::foo", "A::foo");
  }

  static class Main {

    public static void main(String[] args) {
      A.callBarInStartup();
      A.callBarOutsideStartup();
    }
  }

  public static class A {

    private static void foo() {
      System.out.println("A::foo");
    }

    private static void bar() {
      foo();
    }

    public static void callBarInStartup() {
      bar();
    }

    public static void callBarOutsideStartup() {
      bar();
    }
  }
}
