// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.forceproguardcompatibility.defaultctor;

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
public class KeepClassMembersDefaultCtorTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("A()");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultCfRuntime().build();
  }

  public KeepClassMembersDefaultCtorTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(KeepClassMembersDefaultCtorTest.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testCompatR8() throws Exception {
    // R8 retains the default constructor but inserts throw null.
    // TODO(b/248473941): R8 should strip the constructor instead.
    run(testForR8Compat(parameters.getBackend()))
        .assertFailureWithErrorThatThrows(NullPointerException.class);
  }

  @Test
  public void testPG() throws Exception {
    run(testForProguard(ProguardVersion.getLatest()).addDontWarn(getClass()))
        .assertFailureWithErrorThatThrows(NoSuchMethodException.class);
  }

  private TestRunResult<?> run(TestShrinkerBuilder<?, ?, ?, ?, ?> builder) throws Exception {
    return builder
        .addInnerClasses(KeepClassMembersDefaultCtorTest.class)
        .addKeepRules("-keepclassmembers class * { <fields>; }")
        .addKeepClassAndMembersRules(TestClass.class)
        .addDontObfuscate()
        .run(parameters.getRuntime(), TestClass.class);
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
              TestClass.class.getPackage().getName() + ".KeepClassMembersDefaultCtorTest$" + name);
      Object obj = clazz.getConstructor().newInstance();
      if (args.length > 0) {
        // Use the field so we are sure that the keep rule triggers.
        A a = getA();
        System.out.println(a.x);
      }
    }
  }
}
