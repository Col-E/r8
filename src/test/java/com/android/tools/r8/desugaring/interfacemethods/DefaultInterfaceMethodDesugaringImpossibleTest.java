// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugaring.interfacemethods;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DefaultInterfaceMethodDesugaringImpossibleTest extends TestBase {

  private static final String EXPECTED = StringUtils.lines("I.m()");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public DefaultInterfaceMethodDesugaringImpossibleTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private Collection<Class<?>> getProgramClasses() {
    return ImmutableList.of(TestClass.class, I.class);
  }

  private Collection<byte[]> getProgramClassData() throws Exception {
    return ImmutableList.of(
        transformer(A.class)
            .setAccessFlags(
                A.class.getDeclaredMethod("m"),
                flags -> {
                  assert flags.isPublic();
                  flags.unsetPublic();
                  flags.setPrivate();
                  flags.setStatic();
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
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    try {
      testForD8()
          .addProgramClasses(getProgramClasses())
          .addProgramClassFileData(getProgramClassData())
          .setMinApi(parameters)
          .compileWithExpectedDiagnostics(this::checkDesugarError)
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(EXPECTED);
      assertTrue(parameters.canUseDefaultAndStaticInterfaceMethods());
    } catch (CompilationFailedException e) {
      assertFalse(parameters.canUseDefaultAndStaticInterfaceMethods());
    }
  }

  @Test
  public void testR8() throws Exception {
    try {
      testForR8(parameters.getBackend())
          .addProgramClasses(getProgramClasses())
          .addProgramClassFileData(getProgramClassData())
          .addKeepAllClassesRule()
          .setMinApi(parameters)
          .compileWithExpectedDiagnostics(this::checkDesugarError)
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(EXPECTED);
      assertTrue(parameters.canUseDefaultAndStaticInterfaceMethods());
    } catch (CompilationFailedException e) {
      assertFalse(parameters.canUseDefaultAndStaticInterfaceMethods());
    }
  }

  private void checkDesugarError(TestDiagnosticMessages diagnostics) {
    if (!parameters.canUseDefaultAndStaticInterfaceMethods()) {
      diagnostics.assertErrorMessageThatMatches(containsString("forwarding method that conflicts"));
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      I a = new A();
      a.m();
    }
  }

  interface I {

    default void m() {
      System.out.println("I.m()");
    }
  }

  static class A implements I {

    public /* will be: private static */ void m() {
      System.out.println("A.m()");
    }
  }
}
