// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.forceproguardcompatibility.defaultctor;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KeepClassesWithMembersDefaultCtorTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("A()");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultCfRuntime().build();
  }

  public KeepClassesWithMembersDefaultCtorTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(KeepClassesWithMembersDefaultCtorTest.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testCompatR8() throws Exception {
    checkInitKept(run(testForR8Compat(parameters.getBackend())));
  }

  @Test
  public void testR8() throws Exception {
    checkInitNotKept(run(testForR8(parameters.getBackend())));
  }

  @Test
  public void testPG() throws Exception {
    checkInitKept(run(testForProguard(ProguardVersion.getLatest()).addDontWarn(getClass())));
  }

  private TestRunResult<?> run(TestShrinkerBuilder<?, ?, ?, ?, ?> builder) throws Exception {
    return builder
        .addInnerClasses(KeepClassesWithMembersDefaultCtorTest.class)
        .addKeepRules("-keepclasseswithmembers class * { <fields>; }")
        .addKeepClassAndMembersRules(TestClass.class)
        .addDontObfuscate()
        .run(parameters.getRuntime(), TestClass.class);
  }

  private TestRunResult<?> checkInitKept(TestRunResult<?> result) throws Exception {
    return result
        .inspect(inspector -> assertThat(inspector.clazz(A.class).init(), isPresent()))
        .assertSuccessWithOutput(EXPECTED);
  }

  private TestRunResult<?> checkInitNotKept(TestRunResult<?> result) throws Exception {
    return result
        .inspectFailure(inspector -> assertThat(inspector.clazz(A.class).init(), isAbsent()))
        .assertFailureWithErrorThatThrows(NoSuchMethodException.class);
  }

  static class A {

    public long x = System.nanoTime();

    public A() {
      System.out.println("A()");
    }
  }

  static class TestClass {

    public static A getA() {
      // Since TestClass.A is hard kept, the shrinker can't assume anything about the return value.
      return null;
    }

    public static void main(String[] args) throws Exception {
      String name = args.length == 0 ? "A" : null;
      Class<?> clazz =
          Class.forName(
              TestClass.class.getPackage().getName()
                  + ".KeepClassesWithMembersDefaultCtorTest$"
                  + name);
      Object obj = clazz.getConstructor().newInstance();
      if (args.length > 0) {
        // Use the field so we are sure that the keep rule triggers.
        A a = getA();
        System.out.println(a.x);
      }
    }
  }
}
