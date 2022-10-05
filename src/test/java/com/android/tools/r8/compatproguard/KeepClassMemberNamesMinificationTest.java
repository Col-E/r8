// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.compatproguard;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeepClassMemberNamesMinificationTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public KeepClassMemberNamesMinificationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testForRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class, A.class, ReflectiveCallerOfA.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World!", "Hello World!");
  }

  @Test
  public void testPG() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testKeepNames(testForProguard(ProguardVersion.V7_0_0).addKeepRules("-dontwarn"));
  }

  @Test
  public void testR8Compat() throws Exception {
    testKeepNames(
        testForR8Compat(parameters.getBackend())
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations());
  }

  @Test
  public void testR8Full() throws Exception {
    testKeepNames(
        testForR8(parameters.getBackend())
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations());
  }

  private void testKeepNames(TestShrinkerBuilder<?, ?, ?, ?, ?> shrinkerBuilder) throws Exception {
    shrinkerBuilder
        .addProgramClasses(Main.class, A.class, ReflectiveCallerOfA.class)
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRulesWithAllowObfuscation(A.class)
        .addKeepRules("-keepclassmembernames class ** { *; }")
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(
            inspector -> {
              ClassSubject aClass = inspector.clazz(A.class);
              assertThat(aClass, isPresentAndRenamed());
              assertThat(aClass.uniqueFieldWithOriginalName("foo"), isPresentAndNotRenamed());
              assertThat(aClass.uniqueMethodWithOriginalName("foo"), isPresentAndNotRenamed());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World!", "Hello World!");
  }

  @NeverClassInline
  public static class A {

    public String foo = "Hello World!";

    public void foo() {
      System.out.println("Hello World!");
    }

    @NeverInline
    public void callMySelf() throws Exception {
      ReflectiveCallerOfA.callA(this.getClass().getName());
    }
  }

  public static class ReflectiveCallerOfA {

    @NeverInline
    public static void callA(String className) throws Exception {
      Class<?> aClass = Class.forName(className);
      Object o = aClass.getDeclaredConstructor().newInstance();
      System.out.println(aClass.getDeclaredField("foo").get(o));
      aClass.getDeclaredMethod("foo").invoke(o);
    }
  }

  public static class Main {

    public static void main(String[] args) throws Exception {
      new A().callMySelf();
    }
  }
}
