// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.allowshrinking;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static org.hamcrest.CoreMatchers.not;
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
public class KeepAllowShrinkingIncludeDescriptorClassesCompatibilityTest extends TestBase {

  private final TestParameters parameters;
  private final Shrinker shrinker;

  @Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(), ImmutableList.of(Shrinker.R8, Shrinker.PG));
  }

  public KeepAllowShrinkingIncludeDescriptorClassesCompatibilityTest(
      TestParameters parameters, Shrinker shrinker) {
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
        .addProgramClasses(TestClass.class, A.class, B.class, SoftPinned.class)
        .addKeepClassAndMembersRules(TestClass.class)
        .addKeepRules(
            "-keepclassmembers,allowshrinking,includedescriptorclasses class "
                + SoftPinned.class.getTypeName()
                + " { <methods>; }")
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("true")
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(TestClass.class), isPresent());

              ClassSubject softPinnedClass = inspector.clazz(SoftPinned.class);
              assertThat(softPinnedClass.uniqueMethodWithName("used"), isPresentAndNotRenamed());
              assertThat(softPinnedClass.uniqueMethodWithName("unused"), not(isPresent()));

              // SoftPinned.used(A) remains thus A must be present and not renamed.
              assertThat(inspector.clazz(A.class), isPresentAndNotRenamed());

              // TODO(b/171548534): Unexpectedly the behavior here is that the class B is also not
              //  renamed. It appears that both R8 and PG will eagerly mark the name as not needing
              //  to be  renamed.
              assertThat(inspector.clazz(B.class), isPresentAndNotRenamed());
            });
  }

  static class A {
    @Override
    public String toString() {
      return getClass().getTypeName();
    }
  }

  static class B {
    @Override
    public String toString() {
      return getClass().getTypeName();
    }
  }

  static class SoftPinned {

    public static void used(A a) {
      System.out.println(a.toString().endsWith(System.nanoTime() > 0 ? "A" : "junk"));
    }

    public static void unused(B b) {
      System.out.println(b.toString().endsWith(System.nanoTime() > 0 ? "B" : "junk"));
    }
  }

  static class TestClass {

    // Kept helper so the classes A and B escape and prohibit removal.
    public static void kept(Object o) {
      if (System.nanoTime() < 0) {
        System.out.println(o.toString());
      }
    }

    public static void main(String[] args) {
      A a = new A();
      B b = new B();
      kept(a);
      kept(b);
      SoftPinned.used(a);
    }
  }
}
