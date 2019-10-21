// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
// This is a reproduction of b/142909854 where we vertically merge a class with a constructor that
// references a class outside the main-dex collection. We did not inline those, even when
// force-inlining, so the renamed constructor broke the init chain.
public class VerticalClassMergerInitTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().build();
  }

  public VerticalClassMergerInitTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testMergingClassWithConstructorNotInMainDex()
      throws IOException, CompilationFailedException, ExecutionException {
    // TODO(b/142909854): Fix test expectation when bug has been resolved.
    testForR8(parameters.getBackend())
        .addInnerClasses(VerticalClassMergerInitTest.class)
        .addKeepMainRule(Main.class)
        .addMainDexRules("-keep class " + Main.class.getTypeName())
        .setMinApi(AndroidApiLevel.K_WATCH)
        .addOptionsModification(
            options -> {
              options.forceProguardCompatibility = true;
              // The initializer is small in this example so only allow FORCE inlining.
              options.testing.validInliningReasons = ImmutableSet.of(Reason.FORCE);
            })
        .compile()
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(Base.class), not(isPresent()));
              assertThat(inspector.clazz(Child.class), isPresent());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatMatches(containsString("initialized"));
  }

  public static class Base {

    public Base() {
      System.out.println("Base.init()");
      new Outside();
    }
  }

  private static class Outside {
    Outside() {
      System.out.println("Outside.init()");
    }
  }

  public static class Child extends Base {

    // We need a static member to force the main-dex tracing to include Child and Base.
    public static Object foo() {
      return null;
    }

    public Child() {
      System.out.println("Child.init()");
    }
  }

  public static class Main {

    {
      if (Child.foo() == null) {
        System.out.println("Main.clinit()");
      }
    }

    public static void main(String[] args) {
      new Child();
    }
  }
}
