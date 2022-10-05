// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.nonnull;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RequireNonNullTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public RequireNonNullTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(RequireNonNullTest.class)
        .addKeepMainRule(TestClass.class)
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .enableInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Live!", "Live!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertThat(testClassSubject.uniqueMethodWithOriginalName("live"), isPresent());
    assertThat(testClassSubject.uniqueMethodWithOriginalName("dead"), not(isPresent()));
  }

  static class TestClass {

    public static void main(String[] args) {
      Object a = System.currentTimeMillis() >= 0 ? new Object() : null;
      Objects.requireNonNull(a);
      if (a != null) {
        live();
      } else {
        dead();
      }

      Object b = System.currentTimeMillis() >= 0 ? new Object() : null;
      Object c = Objects.requireNonNull(b);
      if (c != null) {
        live();
      } else {
        dead();
      }
    }

    @NeverInline
    static void live() {
      System.out.println("Live!");
    }

    @NeverInline
    static void dead() {
      System.out.println("Dead!");
    }
  }
}
