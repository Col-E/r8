// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.unusedarguments;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

public abstract class UnusedArgumentsTestBase extends TestBase {

  private final boolean minification;

  public UnusedArgumentsTestBase(boolean minification) {
    this.minification = minification;
  }

  @Parameters(name = "minification:{0}")
  public static Collection<Object[]> data() {
    return ImmutableList.of(new Object[] {true}, new Object[] {false});
  }

  public abstract Class<?> getTestClass();

  public Collection<Class<?>> getAdditionalClasses() {
    return ImmutableList.of();
  }

  public abstract String getExpectedResult();

  public void inspectTestClass(ClassSubject clazz) {}

  public void inspect(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(getTestClass());
    assertThat(clazz, isPresent());
    inspectTestClass(clazz);
  }

  @Test
  public void test() throws Throwable {
    testForJvm()
        .addTestClasspath()
        .run(getTestClass())
        .assertSuccessWithOutput(getExpectedResult());

    testForR8(Backend.DEX)
        .addProgramClasses(getTestClass())
        .addProgramClasses(getAdditionalClasses())
        .addKeepMainRule(getTestClass())
        .addKeepRules(minification ? "" : "-dontobfuscate")
        .enableInliningAnnotations()
        .addOptionsModification(InternalOptions::enableUnusedArgumentRemoval)
        .compile()
        .inspect(this::inspect)
        .run(getTestClass())
        .assertSuccessWithOutput(getExpectedResult());
  }
}
