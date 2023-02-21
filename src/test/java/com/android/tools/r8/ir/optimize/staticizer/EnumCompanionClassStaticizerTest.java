// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.staticizer;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
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
public class EnumCompanionClassStaticizerTest extends TestBase {

  private final boolean enableEnumUnboxing;
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{1}, enum unboxing: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public EnumCompanionClassStaticizerTest(boolean enableEnumUnboxing, TestParameters parameters) {
    this.enableEnumUnboxing = enableEnumUnboxing;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class, MyEnum.class, Companion.class)
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(options -> options.enableEnumUnboxing = enableEnumUnboxing)
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject companionClassSubject = inspector.clazz(Companion.class);
    // TODO(b/162798790): Should be absent.
    assertThat(companionClassSubject, isPresent());
  }

  static class TestClass {

    public static void main(String[] args) {
      MyEnum.A.companion.greet();
    }
  }

  enum MyEnum {
    A;

    public static final Companion companion = new Companion();
  }

  @NeverClassInline
  static class Companion {

    @NeverInline
    public void greet() {
      System.out.println("Hello world!");
    }
  }
}
