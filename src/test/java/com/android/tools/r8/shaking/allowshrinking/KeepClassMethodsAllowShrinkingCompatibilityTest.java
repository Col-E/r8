// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.allowshrinking;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KeepClassMethodsAllowShrinkingCompatibilityTest extends TestBase {

  private final TestParameters parameters;
  private final boolean allowOptimization;
  private final boolean allowObfuscation;
  private final Shrinker shrinker;

  @Parameterized.Parameters(name = "{0}, opt:{1}, obf:{2}, {3}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        BooleanUtils.values(),
        BooleanUtils.values(),
        ImmutableList.of(Shrinker.R8, Shrinker.PG));
  }

  public KeepClassMethodsAllowShrinkingCompatibilityTest(
      TestParameters parameters,
      boolean allowOptimization,
      boolean allowObfuscation,
      Shrinker shrinker) {
    this.parameters = parameters;
    this.allowOptimization = allowOptimization;
    this.allowObfuscation = allowObfuscation;
    this.shrinker = shrinker;
  }

  String getExpected() {
    return StringUtils.lines(
        "A::foo",
        // Reflective lookup of A::foo will only work if optimization and obfuscation are disabled.
        Boolean.toString(!allowOptimization && !allowObfuscation),
        "false");
  }

  @Test
  public void test() throws Exception {
    if (shrinker.isR8()) {
      run(
          testForR8(parameters.getBackend())
              // Allowing all of shrinking, optimization and obfuscation will amount to a nop rule.
              .allowUnusedProguardConfigurationRules(allowOptimization && allowObfuscation));
    } else {
      run(testForProguard(shrinker.getProguardVersion()).addDontWarn(getClass()));
    }
  }

  public <T extends TestShrinkerBuilder<?, ?, ?, ?, T>> void run(T builder) throws Exception {
    String keepRule =
        "-keepclassmembers,allowshrinking"
            + (allowOptimization ? ",allowoptimization" : "")
            + (allowObfuscation ? ",allowobfuscation" : "")
            + " class * { java.lang.String foo(); java.lang.String bar(); }";
    builder
        .addInnerClasses(KeepClassMethodsAllowShrinkingCompatibilityTest.class)
        .addKeepClassAndMembersRules(TestClass.class)
        .addKeepRules(keepRule)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), TestClass.class, A.class.getTypeName())
        .assertSuccessWithOutput(getExpected())
        .inspect(
            inspector -> {
              ClassSubject aClass = inspector.clazz(A.class);
              ClassSubject bClass = inspector.clazz(B.class);
              // The class constants will force A and B to be retained, but not the methods.
              assertThat(bClass, isPresentAndRenamed());
              assertThat(bClass.uniqueMethodWithName("foo"), not(isPresent()));
              assertThat(bClass.uniqueMethodWithName("bar"), not(isPresent()));

              assertThat(aClass, isPresentAndRenamed());
              // The dependent rule with soft-pinning of bar never causes A::bar to be retained
              // regardless of A and A::foo being retained.
              assertThat(aClass.uniqueMethodWithName("bar"), not(isPresent()));
              MethodSubject aFoo = aClass.uniqueMethodWithName("foo");
              if (allowOptimization) {
                assertThat(aFoo, not(isPresent()));
              } else {
                assertThat(aFoo, isPresentAndRenamed(allowObfuscation));
                assertThat(inspector.clazz(TestClass.class).mainMethod(), invokesMethod(aFoo));
              }
            });
  }

  static class A {
    public String foo() {
      return "A::foo";
    }

    public String bar() {
      return "A::bar";
    }
  }

  static class B {
    public String foo() {
      return "B::foo";
    }

    public String bar() {
      return "B::bar";
    }
  }

  static class TestClass {

    public static boolean hasFoo(String name) {
      try {
        return Class.forName(name).getDeclaredMethod("foo") != null;
      } catch (Exception e) {
        return false;
      }
    }

    public static void main(String[] args) {
      // Direct call to A.foo, if optimization is not allowed it will be kept.
      A a = new A();
      System.out.println(a.foo());
      // Reference to A should not retain A::foo when allowoptimization is set.
      // Note: if using class constant A.class, PG will actually retain A::foo !?
      System.out.println(hasFoo(a.getClass().getTypeName()));
      // Reference to B should not retain B::foo regardless of allowoptimization.
      System.out.println(hasFoo(B.class.getTypeName()));
    }
  }
}
