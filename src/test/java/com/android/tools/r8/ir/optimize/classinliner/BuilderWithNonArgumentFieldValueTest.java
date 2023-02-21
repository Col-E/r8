// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class BuilderWithNonArgumentFieldValueTest extends TestBase {

  private final boolean enableClassInlining;
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{1}, class inlining: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public BuilderWithNonArgumentFieldValueTest(
      boolean enableClassInlining, TestParameters parameters) {
    this.enableClassInlining = enableClassInlining;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(BuilderWithNonArgumentFieldValueTest.class)
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(options -> options.enableClassInlining = enableClassInlining)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject builderClassSubject = inspector.clazz(Builder.class);
    assertNotEquals(enableClassInlining, builderClassSubject.isPresent());
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(new Builder().world().build());
    }
  }

  static class Builder {

    String suffix;

    Builder() {
      System.out.print("Hello");
      this.suffix = "!";
    }

    @NeverInline
    Builder world() {
      System.out.print(" world");
      return this;
    }

    @NeverInline
    String build() {
      return suffix;
    }
  }
}
