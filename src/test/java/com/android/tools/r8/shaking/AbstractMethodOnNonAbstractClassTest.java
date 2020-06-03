// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.Dex2OatTestRunResult;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AbstractMethodOnNonAbstractClassTest extends TestBase {

  private static final String DEX2OAT_WARNING =
      "is abstract, but the declaring class is neither abstract nor an interface";

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public AbstractMethodOnNonAbstractClassTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testCompat() throws Exception {
    R8TestCompileResult compileResult =
        testForR8Compat(parameters.getBackend())
            .addInnerClasses(AbstractMethodOnNonAbstractClassTest.class)
            .addKeepMainRule(TestClass.class)
            .setMinApi(parameters.getApiLevel())
            .compile();

    // A is not made abstract in compat mode.
    ClassSubject classSubject = compileResult.inspector().clazz(A.class);
    assertThat(classSubject, isPresent());
    assertFalse(classSubject.isAbstract());

    MethodSubject methodSubject = classSubject.uniqueMethodWithName("m");
    assertThat(methodSubject, isPresent());

    if (parameters.isDexRuntime()) {
      Dex2OatTestRunResult dex2OatResult = compileResult.runDex2Oat(parameters.getRuntime());
      if (parameters.getApiLevel().isLessThan(AndroidApiLevel.L)) {
        // Dalvik does not allow abstract methods on non-abstract classes, so R8 emits an empty
        // throwing method instead.
        assertFalse(methodSubject.isAbstract());
        dex2OatResult.assertStderrMatches(not(containsString(DEX2OAT_WARNING)));
      } else {
        // Verify that the method has become abstract.
        assertTrue(methodSubject.isAbstract());

        DexVm.Version version = parameters.getRuntime().asDex().getVm().getVersion();
        if (version.equals(Version.V5_1_1) || version.equals(Version.V6_0_1)) {
          // Art 5.1.1 and 6.0.1 did not report a warning for having an abstract method on a
          // non-abstract class.
          dex2OatResult.assertStderrMatches(not(containsString(DEX2OAT_WARNING)));
        } else {
          dex2OatResult.assertStderrMatches(containsString(DEX2OAT_WARNING));
        }
      }
    }

    compileResult
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  @Test
  public void testFull() throws Exception {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addInnerClasses(AbstractMethodOnNonAbstractClassTest.class)
            .addKeepMainRule(TestClass.class)
            .setMinApi(parameters.getApiLevel())
            .compile();

    // A is made abstract in full mode.
    ClassSubject classSubject = compileResult.inspector().clazz(A.class);
    assertThat(classSubject, isPresent());
    assertTrue(classSubject.isAbstract());

    // A.m() is also made abstract in full mode.
    MethodSubject methodSubject = classSubject.uniqueMethodWithName("m");
    assertThat(methodSubject, isPresent());
    assertTrue(methodSubject.isAbstract());

    if (parameters.isDexRuntime()) {
      // There is no warning due to both A and A.m() being abstract.
      compileResult
          .runDex2Oat(parameters.getRuntime())
          .assertStderrMatches(not(containsString(DEX2OAT_WARNING)));
    }

    compileResult
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  static class TestClass {

    public static void main(String[] args) {
      A b = System.currentTimeMillis() > 0 ? new B() : new C();
      b.m();
    }
  }

  static class A {

    // Never called directly on A, thus can be made abstract.
    void m() {}
  }

  static class B extends A {

    @Override
    void m() {
      System.out.println("Hello world!");
    }
  }

  static class C extends A {

    @Override
    void m() {
      throw new RuntimeException();
    }
  }
}
