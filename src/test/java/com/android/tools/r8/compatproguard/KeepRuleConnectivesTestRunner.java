// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compatproguard;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.BaseCompilerCommand;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.compatproguard.KeepRuleConnectivesTest.BothFooAndBar;
import com.android.tools.r8.compatproguard.KeepRuleConnectivesTest.JustBar;
import com.android.tools.r8.compatproguard.KeepRuleConnectivesTest.JustFoo;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeepRuleConnectivesTestRunner extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public KeepRuleConnectivesTestRunner(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private <
          C extends BaseCompilerCommand,
          B extends BaseCompilerCommand.Builder<C, B>,
          CR extends TestCompileResult<CR, RR>,
          RR extends TestRunResult<RR>,
          T extends TestShrinkerBuilder<C, B, CR, RR, T>>
      void testKeepIsDisjunction(TestShrinkerBuilder<C, B, CR, RR, T> builder) throws Exception {
    builder
        .addProgramClassesAndInnerClasses(KeepRuleConnectivesTest.class)
        .addKeepRules("-keep class * { public long foo(); public long bar(); }")
        .compile()
        .inspect(
            inspector -> {
              ClassSubject bothFooAndBar = inspector.clazz(BothFooAndBar.class);
              ClassSubject justFoo = inspector.clazz(JustFoo.class);
              ClassSubject justBar = inspector.clazz(JustBar.class);
              assertTrue(bothFooAndBar.isPresent());
              assertTrue(bothFooAndBar.uniqueMethodWithOriginalName("foo").isPresent());
              assertTrue(bothFooAndBar.uniqueMethodWithOriginalName("bar").isPresent());
              assertTrue(justFoo.isPresent());
              assertTrue(justFoo.uniqueMethodWithOriginalName("foo").isPresent());
              assertTrue(justBar.isPresent());
              assertTrue(justBar.uniqueMethodWithOriginalName("bar").isPresent());
            });
  }

  @Test
  public void testKeepIsDisjunctionPG() throws Exception {
    testKeepIsDisjunction(testForProguard());
  }

  @Test
  public void testKeepIsDisjunctionCompatR8() throws Exception {
    testKeepIsDisjunction(testForR8Compat(Backend.DEX));
  }

  @Test
  public void testKeepIsDisjunctionFullR8() throws Exception {
    testKeepIsDisjunction(testForR8(Backend.DEX));
  }

  private <
      C extends BaseCompilerCommand,
      B extends BaseCompilerCommand.Builder<C, B>,
      CR extends TestCompileResult<CR, RR>,
      RR extends TestRunResult<RR>,
      T extends TestShrinkerBuilder<C, B, CR, RR, T>>
  void testKeepClassesWithMembersIsConjunction(TestShrinkerBuilder<C, B, CR, RR, T> builder)
      throws Exception {
    builder
        .addProgramClassesAndInnerClasses(KeepRuleConnectivesTest.class)
        .addKeepRules("-keepclasseswithmembers class * { public long foo(); public long bar(); }")
        .compile()
        .inspect(
            inspector -> {
              ClassSubject bothFooAndBar = inspector.clazz(BothFooAndBar.class);
              ClassSubject justFoo = inspector.clazz(JustFoo.class);
              ClassSubject justBar = inspector.clazz(JustBar.class);
              assertTrue(bothFooAndBar.isPresent());
              assertTrue(bothFooAndBar.uniqueMethodWithOriginalName("foo").isPresent());
              assertTrue(bothFooAndBar.uniqueMethodWithOriginalName("bar").isPresent());
              assertFalse(justFoo.isPresent());
              assertFalse(justBar.isPresent());
            });
  }

  @Test
  public void testKeepIsConjunctionPG() throws Exception {
    testKeepClassesWithMembersIsConjunction(testForProguard());
  }

  @Test
  public void testKeepIsConjunctionCompatR8() throws Exception {
    testKeepClassesWithMembersIsConjunction(testForR8Compat(Backend.DEX));
  }

  @Test
  public void testKeepIsConjunctionFullR8() throws Exception {
    testKeepClassesWithMembersIsConjunction(testForR8(Backend.DEX));
  }
}
