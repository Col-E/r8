// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.allowshrinking;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeepClassAllowShrinkingCompatibilityTest extends TestBase {

  private final TestParameters parameters;
  private final Shrinker shrinker;

  @Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(), ImmutableList.of(Shrinker.R8, Shrinker.PG));
  }

  public KeepClassAllowShrinkingCompatibilityTest(TestParameters parameters, Shrinker shrinker) {
    this.parameters = parameters;
    this.shrinker = shrinker;
  }

  @Test
  public void test() throws Exception {
    if (shrinker.isPG()) {
      run(testForProguard(shrinker.getProguardVersion()).addDontWarn(getClass()));
    } else {
      run(testForR8(parameters.getBackend()));
    }
  }

  private <T extends TestShrinkerBuilder<?, ?, ?, ?, T>> void run(T builder) throws Exception {
    builder
        .addProgramClasses(A.class, B.class, Main.class)
        .addKeepMainRule(Main.class)
        .addKeepRules("-keep,allowshrinking" + " class " + A.class.getTypeName())
        .addKeepRules("-keep,allowshrinking" + " class " + B.class.getTypeName())
        .compile()
        .inspect(
            inspector -> {
              ClassSubject aSubject = inspector.clazz(A.class);
              assertThat(aSubject, isPresent());
              ClassSubject bSubject = inspector.clazz(B.class);
              assertThat(bSubject, isPresent());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithEmptyOutput();
  }

  static class A {
    public A(int i) {
      // Non-default constructor to ensure no soft pinning of a members.
      if (i == 42) {
        throw new RuntimeException("Foo");
      }
    }
  }

  static class B {
    public B(int i) {
      // Non-default constructor to ensure no soft pinning of a members.
      if (i == 42) {
        throw new RuntimeException("Foo");
      }
    }
  }

  static class Main {

    public static void main(String[] args) {
      new A(args.length);
      new B(args.length);
    }
  }
}
