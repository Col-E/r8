// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugaring.interfacemethods;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DefaultInterfaceMethodDesugaringWithPublicStaticResolutionOnClassTest
    extends TestBase {

  private static final String EXPECTED_INVALID = StringUtils.lines("I.m()");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public DefaultInterfaceMethodDesugaringWithPublicStaticResolutionOnClassTest(
      TestParameters parameters) {
    this.parameters = parameters;
  }

  private Collection<Class<?>> getProgramClasses() {
    return ImmutableList.of(TestClass.class, I.class, B.class);
  }

  private Collection<byte[]> getProgramClassData() throws Exception {
    return ImmutableList.of(
        transformer(A.class)
            .setAccessFlags(
                A.class.getDeclaredMethod("m"),
                flags -> {
                  assertTrue(flags.isPrivate());
                  assertTrue(flags.isStatic());
                  flags.promoteToPublic();
                })
            .transform());
  }

  @Test
  public void testJVM() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(getProgramClasses())
        .addProgramClassFileData(getProgramClassData())
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClasses(getProgramClasses())
        .addProgramClassFileData(getProgramClassData())
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            parameters.canUseDefaultAndStaticInterfaceMethodsWhenDesugaring()
                && parameters.isDexRuntimeVersion(Version.V7_0_0),
            r -> r.assertSuccessWithOutput(EXPECTED_INVALID),
            r -> r.assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class));
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addProgramClasses(getProgramClasses())
        .addProgramClassFileData(getProgramClassData())
        .addKeepAllClassesRule()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class);
  }

  static class TestClass {

    public static void main(String[] args) {
      new B().m();
    }
  }

  interface I {

    default void m() {
      System.out.println("I.m()");
    }
  }

  public static class A {

    private static /* will be: public static */ void m() {
      System.out.println("A.m()");
    }
  }

  static class B extends A implements I {}
}
