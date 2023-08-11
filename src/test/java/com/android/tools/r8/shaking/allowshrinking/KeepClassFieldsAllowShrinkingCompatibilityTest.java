// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.allowshrinking;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.accessesField;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KeepClassFieldsAllowShrinkingCompatibilityTest extends TestBase {

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

  public KeepClassFieldsAllowShrinkingCompatibilityTest(
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
        "A.foo",
        // R8 will succeed in removing the field if allowoptimization is set.
        Boolean.toString((shrinker.isPG() || !allowOptimization) && !allowObfuscation),
        // R8 will always remove the unreferenced B.foo field.
        Boolean.toString(shrinker.isPG() && !allowObfuscation));
  }

  @Test
  public void test() throws Exception {
    if (shrinker.isR8()) {
      run(testForR8(parameters.getBackend()));
    } else {
      run(testForProguard(shrinker.getProguardVersion()).addDontWarn(getClass()));
    }
  }

  public <T extends TestShrinkerBuilder<?, ?, ?, ?, T>> void run(T builder) throws Exception {
    String keepRule =
        "-keepclassmembers,allowshrinking"
            + (allowOptimization ? ",allowoptimization" : "")
            + (allowObfuscation ? ",allowobfuscation" : "")
            + " class * { java.lang.String foo; java.lang.String bar; }";
    builder
        .addInnerClasses(KeepClassFieldsAllowShrinkingCompatibilityTest.class)
        .addKeepClassAndMembersRules(TestClass.class)
        .addKeepRules(keepRule)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class, A.class.getTypeName())
        .assertSuccessWithOutput(getExpected())
        .inspect(
            inspector -> {
              ClassSubject aClass = inspector.clazz(A.class);
              ClassSubject bClass = inspector.clazz(B.class);
              // The class constants will force A and B to be retained but renamed.
              assertThat(aClass, isPresentAndRenamed());
              assertThat(bClass, isPresentAndRenamed());

              FieldSubject aFoo = aClass.uniqueFieldWithOriginalName("foo");
              FieldSubject aBar = aClass.uniqueFieldWithOriginalName("bar");
              FieldSubject bFoo = bClass.uniqueFieldWithOriginalName("foo");
              FieldSubject bBar = bClass.uniqueFieldWithOriginalName("bar");

              if (allowOptimization) {
                // PG fails to optimize out the referenced field.
                assertThat(aFoo, notIf(isPresent(), shrinker.isR8()));
                assertThat(aBar, notIf(isPresent(), shrinker.isR8()));
                assertThat(bFoo, notIf(isPresent(), shrinker.isR8()));
                assertThat(bBar, notIf(isPresent(), shrinker.isR8()));
              } else {
                assertThat(aFoo, isPresentAndRenamed(allowObfuscation));
                assertThat(
                    aBar, /*shrinker.isR8() ? isAbsent() : */
                    isPresentAndRenamed(allowObfuscation));
                assertThat(inspector.clazz(TestClass.class).mainMethod(), accessesField(aFoo));
                if (shrinker.isR8()) {
                  assertThat(bFoo, not(isPresent()));
                  assertThat(bBar, not(isPresent()));
                } else {
                  assertThat(bFoo, isPresentAndRenamed(allowObfuscation));
                  assertThat(bBar, isPresentAndRenamed(allowObfuscation));
                }
              }
            });
  }

  static class A {
    // Note: If the fields are final PG actually allows itself to inline the values.
    public String foo = "A.foo";
    public String bar = "A.bar";
  }

  static class B {
    public String foo = "B.foo";
    public String bar = "B.bar";
  }

  static class TestClass {

    public static boolean hasFoo(String name) {
      try {
        return Class.forName(name).getDeclaredField("foo") != null;
      } catch (Exception e) {
        return false;
      }
    }

    public static void main(String[] args) {
      // Conditional instance to prohibit class inlining of A.
      A a = args.length == 42 ? null : new A();
      // Direct use of A.foo, if optimization is not allowed it will be kept.
      System.out.println(a.foo);
      // Reference to A should not retain A.foo when allowoptimization is set.
      System.out.println(hasFoo(a.getClass().getTypeName()));
      // Reference to B should not retain B.foo regardless of allowoptimization.
      System.out.println(hasFoo(B.class.getTypeName()));
    }
  }
}
